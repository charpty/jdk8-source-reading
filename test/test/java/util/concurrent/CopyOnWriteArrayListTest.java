package test.java.util.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import junit.framework.TestCase;

public class CopyOnWriteArrayListTest extends TestCase {

	public void testConstructorCowList() {
		String item = new String("item-1");
		CopyOnWriteArrayList<String> init = new CopyOnWriteArrayList<>();
		init.add(item);
		CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>(init);
		assertEquals(1, list.size());
		assertTrue(list.get(0) == item);
		list.add("item-2");
		assertEquals(2, list.size());
		assertEquals(2, init.size());
		list.clear();
		assertEquals(2, init.size());
	}

	public void testBug6260652() {
		// http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6260652
		// 这个问题在JDK9中已经被修复了，个人觉得不太算是一个BUG，只是一个不太直观的设计而已
		List<String> list1 = new ArrayList<>();
		list1.add("str1");
		Object[] arr1 = list1.toArray();
		arr1[0] = "str2";
		// arr1[0] = new Object();
		Sub[] subs = new Sub[] {new Sub()};
		Base[] bases = subs;
		// ArrayStoreException
		// bases[0] = new Base();

	}


	class Base {

	}

	class Sub extends Base {

	}

}
