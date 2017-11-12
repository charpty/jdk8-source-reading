package test.java.util.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import junit.framework.TestCase;
import org.junit.Test;

public class CopyOnWriteArrayListTest extends TestCase {

	@Test
	public void testConstructorCowList() {
		String item = new String("item-1");
		CopyOnWriteArrayList<String> init = new CopyOnWriteArrayList<>();
		init.add(item);
		CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>(init);
		assertEquals(1, list.size());
		assertTrue(list.get(0) == item);
		list.clear();
		assertEquals(1, init.size());
		list.add("item-2");
		assertEquals(1, list.size());
		assertEquals(1, init.size());
	}

	@Test
	public void testArrayCopyOutOfIndex() {
		Object src = new Object[2];
		Object dest = new Object[2];
		// Not throw ArrayIndexOutOfBoundsException
		System.arraycopy(src, 2, dest, 0, 0);
		// throw ArrayIndexOutOfBoundsException
		System.arraycopy(src, 3, dest, 0, 0);
	}

	@Test
	public void testBreakPoint() {
		String a = "a";
		String b = "b";
		int c = 0;
		if (a != b) {
			testPoint1:
			{
				int i = 0;
				testPoint2:
				{
					if (c == 0) {
						System.out.println("go testPoint2");
						break testPoint2;
					}
					assertTrue("break point not work", false);
				}
				System.out.println("begin loop");
				int d = 0;
				for (int j = 0; j < 5; j++) {
					System.out.println("loop...d=" + d);
					outer:
					{
						++c;
						if (d == 2) {
							System.out.println("go outer");
							break outer;
						}
						++d;
						for (i = 0; i < 5; i++) {
							++c;
						}
					}
					if (d > 2) {
						assertTrue("break point not work", false);
					}
					if (c > 13) {
						assertEquals(14, c);
						System.out.println("go testPoint1");
						break testPoint1;
					}
				}
				assertTrue("break point not work", false);
			}
		}
	}

	@Test
	public void testBug6260652() {
		// http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6260652
		// 这个问题在JDK9中已经被修复了，个人觉得不太算是一个BUG，只是一个不太直观的设计而已
		List<String> list1 = new ArrayList<>();
		list1.add("str1");
		Object[] arr1 = list1.toArray();
		arr1[0] = "str2";
		// arr1[0] = new Object();
		Sub[] subs = new Sub[] { new Sub() };
		Base[] bases = subs;
		// ArrayStoreException
		// bases[0] = new Base();
	}

	class Base {

	}

	class Sub extends Base {

	}

}
