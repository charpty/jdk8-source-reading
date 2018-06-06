package test.java.nio;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 读一个有几千万行的字符串文本，每一行都是字母和数字组成，每一行不超过256个字节
 * 读出来之后将其排序输出，按照字符串的自然排序升序
 *
 * 多次测试下来，新生代代大小对性能存在明显影响。
 * 对于1000万文本（800多M）:-Xms4G -Xmx4G -Xmn2500M -XX:+PrintGCDetails
 *
 * @author charpty
 * @since 2018/1/1
 */
public class ReadFileAndSort4 {

    private static final byte[] LINE = "\n".getBytes();
    private static final byte LF = 10;
    // 前16位代表所属固定块数组
    private static final long ARRAY_INDEX_SHIFT = 0x7FFF000000000000L;
    // 第17-50位代表开始地址
    private static final long START_SHIFT = 0x0000FFFFFF000000L;
    private static final long START_MASK = 0xFFFF000000FFFFFFL;
    // 后51-64位代表结束地址
    private static final long END_SHIFT = 0x0000000000FFFFFFL;

    private static final int BUFFER_SIZE = 1024 * 1024 * 4;

    public static void main(String[] args) throws Exception {
        long start = System.nanoTime();
        // 因为题目规定只有字符和数字，所以一共26+26+10=62个桶即可,因为256并不大，所以直接使用每一种长度作为一个桶即可
        // 使用一层桶时效率最高
        PriorityBlockingQueue[] buckets = new PriorityBlockingQueue[62 * 1 * 256];
        ExecutorService es = Executors.newWorkStealingPool();

        // 使用一个long的中间24位存储字符串起始位置，使用低24位存储结束位置，前16位存储在哪个桶中
        byte[][] mem = new byte[1 << 16][];
        FixCharsComparator comparator = new FixCharsComparator(mem);
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new PriorityBlockingQueue(1000, comparator);
        }

        RandomAccessFile raf = new RandomAccessFile(new File("/tmp/large.file"), "r");
        FileChannel channel = raf.getChannel();
        // 一次读4M,2^22
        // 24 + 24 + 16
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        // memory index
        int mi = 0;
        // 1000万个字符串
        long[][] indexArr = new long[1 << 10][3];
        // copy to next map
        HashMap<Integer, Long> ctnm = new HashMap<>(1024);
        // no need copy map
        HashSet<Integer> nncm = new HashSet<>(1024);
        boolean last = false;
        while (true) {
            if ((channel.read(buffer)) < 0) {
                if (last) {
                    break;
                }
                last = true;
            }
            // 保证一次性buffer读满
            if (buffer.remaining() != 0 && !last) {
                continue;
            }
            int position = buffer.position();
            byte[] array = buffer.array();
            byte[] tmpArr = new byte[position + 256];
            // 一次性拷贝好内存，避免大量重复分配与拷贝
            System.arraycopy(array, 0, tmpArr, 256, position);
            final long cmi = mi;
            final boolean clast = last;
            mem[mi++] = tmpArr;
            es.submit(() -> {
                // 每次读4M，使用线程池处理每次读到的数据，每个线程单独操作一个桶，在独立中的桶中计数
                // 使用一个并发MAP处理各次读取的拆包、粘包问题
                int cs = 0;
                final int icmi = (int) cmi;
                // start offset
                long so = 256;
                // current index
                int ci = 256;
                if (tmpArr[ci] == LF) {
                    ci = 257;
                    so = 257;
                    nncm.add(icmi - 1);
                }
                boolean first = true;
                for (int i = ci; i < tmpArr.length; i++) {
                    if (tmpArr[i] == LF) {
                        long ld = i | (so << 24) | (cmi << 48);
                        if (!first) {
                            int iso = (int) so;
                            int len = i - iso;
                            // 先按长度分桶
                            int index = len * 62;
                            // 根据level将元素多级划分到不同桶
                            int offset = 0;
                            byte f = tmpArr[iso];
                            if (f <= '9') {
                                // 只有字符和数字
                                offset = f - '0';
                            } else if (f <= 'Z') {
                                offset = f - 'A' + 10;
                            } else {
                                offset = f - 'a' + 36;
                            }
                            index = index + offset;
                            PriorityBlockingQueue<Long> queue = buckets[index];
                            queue.add(ld);
                        } else {
                            // 前256个用于存放上一个
                            indexArr[icmi][cs++] = ld;
                            first = false;
                        }
                        so = i + 1;
                    }
                }
                // 并发下收集粘包
                if (so < tmpArr.length) {
                    long c = tmpArr.length | (so << 24) | (cmi << 48);
                    if (clast) {
                        indexArr[icmi][cs] = c;
                    } else {
                        ctnm.put((int) cmi, c);
                    }
                }
            });
            buffer.rewind();
        }
        es.shutdown();
        es.awaitTermination(8000, TimeUnit.MILLISECONDS);
        // 处理粘包
        for (Map.Entry<Integer, Long> entry : ctnm.entrySet()) {
            int kmi = entry.getKey();
            long vmi = entry.getValue();

            int s = (int) ((vmi & START_SHIFT) >>> 24);
            int e = (int) (vmi & END_SHIFT);
            int len = e - s;
            int news = 256 - len;
            byte[] dest = mem[kmi + 1];
            if (indexArr[kmi + 1][0] == 0) {
                for (int x = 0; x < 3; x++) {
                    if (indexArr[kmi][x] == 0) {
                        indexArr[kmi][x] = vmi;
                        break;
                    }
                }
            } else if (nncm.contains(kmi)) {
                for (int x = 0; x < 3; x++) {
                    if (indexArr[kmi][x] == 0) {
                        indexArr[kmi][x] = vmi;
                        break;
                    }
                }
            } else {
                System.arraycopy(mem[kmi], s, dest, news, len);
                long l = indexArr[kmi + 1][0];
                l = (l & START_MASK) | (((long) news) << 24);
                indexArr[kmi + 1][0] = l;
            }
        }
        // 按照字母分桶
        for (int i = 0; i < mi; i++) {
            long[] indexs = indexArr[i];
            for (int j = 0; j < indexs.length; j++) {
                long idx = indexs[j];
                if (idx == 0) {
                    break;
                }
                int m = (int) ((idx & ARRAY_INDEX_SHIFT) >>> 48);
                int s = (int) ((idx & START_SHIFT) >>> 24);
                int e = (int) (idx & END_SHIFT);

                int len = e - s;
                // 先按长度分桶
                int index = len * 62;
                // 根据level将元素多级划分到不同桶
                int offset = 0;
                byte f = mem[m][s];
                if (f <= '9') {
                    // 只有字符和数字
                    offset = f - '0';
                } else if (f <= 'Z') {
                    offset = f - 'A' + 10;
                } else {
                    offset = f - 'a' + 36;
                }
                index = index + offset;

                PriorityBlockingQueue<Long> list = buckets[index];
                list.add(idx);
            }
        }
        RandomAccessFile result = new RandomAccessFile(new File("/tmp/result.file"), "rw");
        FileChannel outputChannel = result.getChannel();

        ByteBuffer tmp = ByteBuffer.allocate(BUFFER_SIZE);
        for (int i = 0; i < buckets.length; i++) {
            PriorityBlockingQueue<Long> queue = buckets[i];
            if (queue.isEmpty()) {
                continue;
            }
            // 最长256
            int capacity = queue.size() * 256;
            if (capacity > tmp.capacity()) {
                tmp = ByteBuffer.allocate(capacity);
            }
            for (long q : queue) {
                int m = (int) ((q & ARRAY_INDEX_SHIFT) >>> 48);
                int s = (int) ((q & START_SHIFT) >>> 24);
                int e = (int) (q & END_SHIFT);
                tmp.put(mem[m], s, e - s).put(LINE);
            }
            tmp.flip();
            outputChannel.write(tmp);
            tmp.clear();
        }
        long end = System.nanoTime();
        System.out.println("共耗时" + TimeUnit.NANOSECONDS.toMillis((end - start)));
    }

    static class FixCharsComparator implements Comparator<Long> {

        private final byte[][] mem;

        public FixCharsComparator(byte[][] mem) {
            this.mem = mem;
        }

        @Override
        public int compare(Long l1, Long l2) {
            int m1 = (int) ((l1 & ARRAY_INDEX_SHIFT) >>> 48);
            int s1 = (int) ((l1 & START_SHIFT) >>> 24);
            int e1 = (int) (l1 & END_SHIFT);

            int m2 = (int) ((l2 & ARRAY_INDEX_SHIFT) >>> 48);
            int s2 = (int) ((l2 & START_SHIFT) >>> 24);

            byte[] b1 = mem[m1];
            byte[] b2 = mem[m2];

            // 长度总是相等的
            while (s1 < e1) {
                byte bb1 = b1[++s1];
                byte bb2 = b2[++s2];
                if (bb1 < bb2) {
                    return -1;
                } else if (bb1 > bb2) {
                    return 1;
                }
            }
            return 0;
        }
    }
}

