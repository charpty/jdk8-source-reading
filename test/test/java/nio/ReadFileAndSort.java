package test.java.nio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * 读一个有几千万行的字符串文本，每一行都是字母和数字组成，每一行不超过256个字节
 * 读出来之后将其排序输出，按照字符串的自然排序升序
 *
 * @author charpty
 * @since 2018/1/1
 */
public class ReadFileAndSort {

	public static void main(String[] args) throws IOException {
		// 因为题目规定只有字符和数字，所以一共26+26+10=62个桶即可
		// TODO 先用List，目前仅有一层桶，但测试发现在千万级行数据条件下一层桶是很快的
		// 数据量达到5千万之后则效率明显降低
		int level = 1;
		ArrayList[] buckets = new ArrayList[62 * level];
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
			// 按照首字母分桶
			for (String s : arr) {
				if (s.isEmpty()) {
					continue;
				}
				int index = 0;
				// 根据level将元素多级划分到不同桶
				for (int j = 0; j < level; j++) {
					int offset = 0;
					// TODO 睡了明天出差 @22:36
					char f = s.charAt(j);
					if (f <= '9') {
						// 只有字符和数字
						offset = f - '0';
					} else if (f <= 'Z') {
						offset = f - 'A' + 10;
					} else {
						offset = f - 'a' + 36;
					}
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
	}
}
