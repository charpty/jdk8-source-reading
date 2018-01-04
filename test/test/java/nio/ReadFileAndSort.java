package test.java.nio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 读一个有几千万行的字符串文本，每一行都是字母和数字组成，每一行不超过256个字节
 * 读出来之后将其排序输出，按照字符串的自然排序升序
 *
 * @author charpty
 * @since 2018/1/1
 */
public class ReadFileAndSort {

	private static final byte[] LINE = "\n".getBytes();

	public static void main(String[] args) throws IOException {
		long start = System.nanoTime();
		// 因为题目规定只有字符和数字，所以一共26+26+10=62个桶即可
		// TODO 先用List，目前仅有一层桶，但测试发现在千万级行数据条件下一层桶是很快的
		// 数据量达到5千万之后则效率明显降低
		int level = 1;
		// 最长的一行字符串长度,因为256并不大，所以直接使用每一种长度作为一个桶即可
		int strLen = 256;
		LinkedList[] buckets = new LinkedList[(int) Math.pow(62, level) * strLen];
		RandomAccessFile raf = new RandomAccessFile(new File("/tmp/large.file"), "r");

		FileChannel channel = raf.getChannel();
		ByteBuffer buffer = ByteBuffer.allocate(40960);
		int n = 0;
		int count = 0;
		long readNanos = 0;
		long divideNanos = 0;
		long tmpNanos = 0;
		while (true) {
			long beforeRead = System.nanoTime();
			if ((n = channel.read(buffer)) < 0) {
				break;
			}
			long afterRead = System.nanoTime();
			readNanos = readNanos + (afterRead - beforeRead);

			byte[] array = buffer.array();
			// 自解析，一般来说都是满的
			String str = new String(array, 0, buffer.position());
			int i = str.lastIndexOf('\n');
			tmpNanos = tmpNanos + (System.nanoTime() - afterRead);
			String used = str.substring(0, i);
			String[] arr = used.split("\n");
			count = count + arr.length;
			buffer.rewind();
			if (i < str.length() - 1) {
				buffer.put(str.substring(i + 1, str.length()).getBytes());
			}
			long beforeDivide = System.nanoTime();
			// 按照字母分桶
			for (String s : arr) {
				// 先按长度分桶
				int index = s.length() * (int) Math.pow(62, level);
				// 根据level将元素多级划分到不同桶
				for (int j = 0; j < level; j++) {
					int offset = 0;
					char f = s.charAt(j);
					if (f <= '9') {
						// 只有字符和数字
						offset = f - '0';
					} else if (f <= 'Z') {
						offset = f - 'A' + 10;
					} else {
						offset = f - 'a' + 36;
					}
					int pow = (int) Math.pow(62, level - j - 1);
					index = index + offset * pow;
				}
				LinkedList list = buckets[index];
				if (list == null) {
					list = new LinkedList<String>();
					buckets[index] = list;
				}
				list.add(s);
			}
			divideNanos = divideNanos + (System.nanoTime() - beforeDivide);
		}
		long afterDivide = System.nanoTime();

		// 开始排序
		RandomAccessFile result = new RandomAccessFile(new File("/tmp/result.file"), "rw");
		FixLenStrComparator comparator = new FixLenStrComparator(level);
		FileChannel outputChannel = result.getChannel();

		ExecutorService executor = Executors.newCachedThreadPool();
		final Boolean[] flags = new Boolean[buckets.length];
		// 要尽量保证排在前面的任务先执行
		for (int i = 0; i < buckets.length; i++) {
			LinkedList<String> list = buckets[i];
			if (list == null || list.isEmpty()) {
				continue;
			}
			flags[i] = false;
			final int x = i;
			executor.submit(() -> {
				Collections.sort(list, comparator);
				flags[x] = true;
			});
		}

		// 排序的动作可以并行
		ByteBuffer tmp = ByteBuffer.allocate(40960);
		long writeNanos = 0;
		for (int i = 0; i < flags.length; i++) {
			Boolean flag = flags[i];
			if (flag != null) {
				while (!flag.booleanValue()) {

				}
				LinkedList<String> list = buckets[i];
				// 最长256
				int capacity = list.size() * 256;
				if (capacity > tmp.capacity()) {
					tmp = ByteBuffer.allocate(capacity);
				}
				for (String s : list) {
					tmp.put(s.getBytes()).put(LINE);
				}
				tmp.flip();
				long t1 = System.nanoTime();
				outputChannel.write(tmp);
				writeNanos = writeNanos + (System.nanoTime() - t1);
				tmp.rewind();
				tmp.clear();
			}
		}
		executor.shutdown();
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
}

class FixLenStrComparator implements Comparator<String> {

	private final int level;

	public FixLenStrComparator(int level) {
		this.level = level;
	}

	@Override
	public int compare(String o1, String o2) {
		// 不可能为null
		// 一定是长度相等的
		// 长level个字符一定是相等的
		char[] chars1 = o1.toCharArray();
		char[] chars2 = o2.toCharArray();
		for (int i = level; i < chars1.length; i++) {
			if (chars1[i] > chars2[i]) {
				return 1;
			} else if (chars1[i] < chars2[i]) {
				return -1;
			}
		}
		return 0;
	}
}