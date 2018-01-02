package test.java.nio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * 读一个有几千万行的字符串文本，每一行都是字母和数字组成，每一行不超过256个字节
 * 读出来之后将其排序输出，按照字符串的自然排序升序
 *
 * @author charpty
 * @since 2018/1/1
 */
public class ReadFileAndSort {

	public static void main(String[] args) throws IOException {
		long start = System.nanoTime();
		// 因为题目规定只有字符和数字，所以一共26+26+10=62个桶即可
		// TODO 先用List，目前仅有一层桶，但测试发现在千万级行数据条件下一层桶是很快的
		// 数据量达到5千万之后则效率明显降低
		int level = 1;
		// 最长的一行字符串长度,因为256并不大，所以直接使用每一种长度作为一个桶即可
		int strLen = 256;
		ArrayList[] buckets = new ArrayList[(int) Math.pow(62, level) * strLen];
		RandomAccessFile raf = new RandomAccessFile(new File("/tmp/large.file"), "r");

		FileChannel channel = raf.getChannel();
		ByteBuffer buffer = ByteBuffer.allocate(4096);
		int n = 0;
		int count = 0;
		while ((n = channel.read(buffer)) >= 0) {
			byte[] array = buffer.array();
			// 自解析，一般来说都是满的
			String str = new String(array, 0, buffer.position());
			int i = str.lastIndexOf('\n');
			String used = str.substring(0, i);
			String[] arr = used.split("\n");
			count = count + arr.length;
			buffer.rewind();
			if (i < str.length() - 1) {
				buffer.put(str.substring(i + 1, str.length()).getBytes());
			}
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
				ArrayList list = buckets[index];
				if (list == null) {
					list = new ArrayList();
					buckets[index] = list;
				}
				list.add(s);
			}
		}
		System.out.println(buckets);
		System.out.println("共有" + count + "行");
		// 开始排序
		RandomAccessFile result = new RandomAccessFile(new File("/tmp/result.file"), "rw");
		FixLenStrComparator comparator = new FixLenStrComparator(level);
		FileChannel outputChannel = result.getChannel();

		ByteBuffer tmp = ByteBuffer.allocate(4096);
		for (ArrayList<String> list : buckets) {
			if (list == null || list.isEmpty()) {
				continue;
			}
			Collections.sort(list, comparator);
			// 最长256
			int capacity = list.size() * 256;
			if (capacity > tmp.capacity()) {
				tmp = ByteBuffer.allocate(capacity);
			}
			for (String s : list) {
				tmp.put(s.getBytes()).put("\n".getBytes());
			}
			tmp.flip();
			outputChannel.write(tmp);
			tmp.rewind();
			tmp.clear();
		}
		// 10131015行约21秒
		System.out.println("共耗时" + (System.nanoTime() - start));
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