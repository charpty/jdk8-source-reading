package test.java.nio;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 读一个有几千万行的字符串文本，每一行都是字母和数字组成，每一行不超过256个字节
 * 读出来之后将其排序输出，按照字符串的自然排序升序
 *
 * 多次测试下来，新生代代大小对性能存在明显影响。
 * 对于1000万文本（800多M），新生代1G比较好:-Xms14G -Xmx14G -Xmn12G -XX:+PrintGCDetails
 *
 * @author charpty
 * @since 2018/1/1
 */
public class ReadFileAndSort3 {

	private static final byte[] LINE = "\n".getBytes();
	private static final byte LF = 10;
	// 前16位代表所属固定块数组
	private static final long ARRAY_INDEX_SHIFT = 0x7FFF000000000000L;
	// 第17-50位代表开始地址
	private static final long START_SHIFT = 0x0000FFFFFF000000L;
	private static final long START_MASK = 0xFFFF000000FFFFFFL;
	// 后51-64位代表结束地址
	private static final long END_SHIFT = 0x0000000000FFFFFFL;

	private static final int BUFFER_SIZE = 1024 * 1024 * 2;

	static byte[] BB = "d2bf0555-9458-43bd-8386-7b3d83b01481d2bf0555-9458-43".getBytes();

	public static void main(String[] args) throws Exception {
		long start = System.nanoTime();
		// 因为题目规定只有字符和数字，所以一共26+26+10=62个桶即可
		// 最长的一行字符串长度,因为256并不大，所以直接使用每一种长度作为一个桶即可
		int strLen = 256;
		ExecutorService es = Executors.newWorkStealingPool();

		// 40960个字符中至多有4096个字符串？在我们的场景中是这样的
		// 使用一个long的中间24位存储字符串起始位置，使用低24位存储结束位置
		byte[][] mem = new byte[1 << 16][];
		FixCharsComparator comparator = new FixCharsComparator(mem);

		// 使用一层桶时效率最高
		PriorityBlockingQueue[] buckets = new PriorityBlockingQueue[62 * strLen];
		for (int i = 0; i < buckets.length; i++) {
			buckets[i] = new PriorityBlockingQueue(1000, comparator);
		}
		RandomAccessFile raf = new RandomAccessFile(new File("/tmp/large.file"), "r");

		FileChannel channel = raf.getChannel();
		// 一次读4M,2^22
		// 24 + 24 + 16
		ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
		int total = 0;
		int count = 0;

		long readNanos = 0;
		long divideNanos = 0;
		long tmpNanos = 0;

		int mi = 0;
		// 1000万个字符串
		long[][] indexArr = new long[1 << 11][3];
		final int[] countArr = new int[1 << 10];
		HashMap<Integer, Long> chm = new HashMap<>(1024);
		HashSet<Integer> nnm = new HashSet<>(1024);
		boolean last = false;
		while (true) {
			long beforeRead = System.nanoTime();
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
			//readNanos = readNanos + (afterRead - beforeRead);
			int position = buffer.position();

			byte[] array = buffer.array();
			byte[] tmpArr = new byte[position + 256];
			// 一次性拷贝好内存，避免大量重复分配与拷贝
			System.arraycopy(array, 0, tmpArr, 256, position);
			total = total + position;
			// 自解析，一般来说都是满的
			// 由于都是字母和数字所以对应就是一个byte就是一个char
			// TODO 是否可以引入多线程并发分割
			// int p = 0;
			total = total + BUFFER_SIZE;
			long afterRead = System.nanoTime();
			final int cst = total;
			final long cmi = mi;
			final boolean clast = last;
			mem[mi++] = tmpArr;
			es.submit(() -> {
				try {
					// TODO 额～这里是要固定增长个数而不是长度，个数是没法固定的。。。
					// 每次读4M，使用线程池处理每次读到的数据，每个线程单独操作一个桶，在独立中的桶中计数
					// 使用一个并发MAP处理各次读取的拆包、粘包问题
					int cs = 0;
					final int icmi = (int) cmi;
					long p = 256;
					int ci = 256;
					if (tmpArr[ci] == LF) {
						ci = 257;
						p = 257;
						nnm.add(icmi - 1);
					}
					boolean first = true;
					for (int i = ci; i < tmpArr.length; i++) {
						if (tmpArr[i] == LF) {
							long ld = i | (p << 24) | (cmi << 48);
							if (!first) {
								int ip = (int) p;
								int len = i - ip;
								// 先按长度分桶
								int index = len * 62;
								// 根据level将元素多级划分到不同桶
								int offset = 0;
								byte f = tmpArr[ip];
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
								countArr[icmi]++;
							} else {
								// 前256个用于存放上一个
								indexArr[icmi][cs++] = ld;
								countArr[icmi]++;
								first = false;
							}
							p = i + 1;
						}
					}
					// 并发下收集粘包
					if (p < tmpArr.length) {
						long c = tmpArr.length | (p << 24) | (cmi << 48);
						if (clast) {
							indexArr[icmi][cs] = c;
							countArr[icmi]++;
						} else {
							chm.put((int) cmi, c);
						}
					}
				} catch (Exception ex) {
					ex.printStackTrace();
					throw ex;
				}
			});
			buffer.rewind();
		}
		es.shutdown();
		long c1 = System.nanoTime();
		es.awaitTermination(8000, TimeUnit.MILLISECONDS);
		long c2 = System.nanoTime();
		System.out.println("wodengl:" + TimeUnit.NANOSECONDS.toMillis(c2 - c1));
		System.out.println("t1:" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
		// 处理粘包
		for (Map.Entry<Integer, Long> entry : chm.entrySet()) {
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
						countArr[kmi]++;
						break;
					}
				}
			} else if (nnm.contains(kmi)) {
				for (int x = 0; x < 3; x++) {
					if (indexArr[kmi][x] == 0) {
						indexArr[kmi][x] = vmi;
						countArr[kmi]++;
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
		long beforeDivide = System.nanoTime();
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
		int sum = 0;
		for (int i = 0; i < buckets.length; i++) {
			sum = sum + buckets[i].size();
		}
		System.out.println("sum=" + sum);
		int sum2 = 0;
		for (int i = 0; i < countArr.length; i++) {
			sum2 = sum2 + countArr[i];
		}
		System.out.println("sum2=" + sum);
		divideNanos = divideNanos + (System.nanoTime() - beforeDivide);
		long afterDivide = System.nanoTime();

		//		System.out.println(count);
		//		System.out.println(readNanos);
		//		System.out.println("tmpNanos" + TimeUnit.NANOSECONDS.toMillis(tmpNanos));
		//		System.out.println("divideNanos" + TimeUnit.NANOSECONDS.toMillis(divideNanos));
		System.out.println(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
		//		System.exit(0);
		// 开始排序
		RandomAccessFile result = new RandomAccessFile(new File("/tmp/result.file"), "rw");
		FileChannel outputChannel = result.getChannel();

		// 排序的动作可以并行
		ByteBuffer tmp = ByteBuffer.allocate(4096000);
		long writeNanos = 0;
		for (int i = 0; i < buckets.length; i++) {
			PriorityBlockingQueue<Long> list = buckets[i];
			if (list.isEmpty()) {
				continue;
			}
			// 最长256
			int capacity = list.size() * 256;
			if (capacity > tmp.capacity()) {
				tmp = ByteBuffer.allocate(capacity);
			}
			for (long l : list) {
				int m = (int) ((l & ARRAY_INDEX_SHIFT) >>> 48);
				int s = (int) ((l & START_SHIFT) >>> 24);
				int e = (int) (l & END_SHIFT);
				tmp.put(mem[m], s, e - s).put(LINE);
			}
			tmp.flip();
			long t1 = System.nanoTime();
			outputChannel.write(tmp);
			writeNanos = writeNanos + (System.nanoTime() - t1);
			tmp.rewind();
			tmp.clear();
		}
		long end = System.nanoTime();
		System.out.println("tmp" + TimeUnit.NANOSECONDS.toMillis(tmpNanos));
		System.out.println("共有" + count + "行");
		System.out.println("读操作IO耗时：" + TimeUnit.NANOSECONDS.toMillis(readNanos));
		System.out.println("单独分桶操作耗时：" + TimeUnit.NANOSECONDS.toMillis(divideNanos));
		System.out.println("读取并分桶共耗时：" + TimeUnit.NANOSECONDS.toMillis((afterDivide - start)));
		System.out.println("排序耗时：" + TimeUnit.NANOSECONDS.toMillis((end - afterDivide - writeNanos)));
		System.out.println("写操作IO耗时：" + TimeUnit.NANOSECONDS.toMillis(writeNanos));
		// 10131015行约21秒
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

