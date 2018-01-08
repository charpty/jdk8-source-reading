package test.java.nio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Random;
import java.util.UUID;

/**
 * @author charpty
 * @since 2018/1/1
 */
public class BuildLargeFile {

	private static final byte[] LINE = "\n".getBytes();
	private static final long LC = 10_000_000;

	public static void main(String[] args) throws IOException {
		RandomAccessFile raf = new RandomAccessFile(new File("/tmp/large.file"), "rw");
		FileChannel channel = raf.getChannel();
		ByteBuffer buffer = ByteBuffer.allocate(256);
		int n = 0;
		Random random = new Random();
		random.setSeed(100);
		int count = 0;
		while (true) {
			String str = UUID.randomUUID().toString();
			buffer.put(str.getBytes());
			buffer.put(str.substring(0, random.nextInt(31)).getBytes());
			int loop = random.nextInt(3);
			for (int i = 0; i < loop; i++) {
				buffer.put(UUID.randomUUID().toString().getBytes());
			}
			buffer.put(LINE);
			buffer.flip();
			channel.write(buffer);
			buffer.clear();
			if (count++ > LC) {
				break;
			}
			// System.out.println("第" + ++n + "次写入...");
		}
		System.out.println(LC + "行写入完毕");
	}

}
