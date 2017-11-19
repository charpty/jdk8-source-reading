/*
 * Copyright (c) 1998, 2011, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package java.util;

/**
 * A {@link Map} that further provides a <em>total ordering</em> on its keys.
 * The map is ordered according to the {@linkplain Comparable natural
 * ordering} of its keys, or by a {@link Comparator} typically
 * provided at sorted map creation time.  This order is reflected when
 * iterating over the sorted map's collection views (returned by the
 * {@code entrySet}, {@code keySet} and {@code values} methods).
 * Several additional operations are provided to take advantage of the
 * ordering.  (This interface is the map analogue of {@link SortedSet}.)
 *
 * 该接口扩展了Map接口并提供了基于键排序的能力。
 * 排序是依据键实现的Comparable接口（自然顺序），或者使用构造Map时指定的Comparator比较器进行比较。
 * 不论是获取entrySet还是keySet或者值set都是遵循这个基于键的排序的策略。
 * 该接口也提供了多个方法来适配这种排序能力。
 *
 * <p>All keys inserted into a sorted map must implement the {@code Comparable}
 * interface (or be accepted by the specified comparator).  Furthermore, all
 * such keys must be <em>mutually comparable</em>: {@code k1.compareTo(k2)} (or
 * {@code comparator.compare(k1, k2)}) must not throw a
 * {@code ClassCastException} for any keys {@code k1} and {@code k2} in
 * the sorted map.  Attempts to violate this restriction will cause the
 * offending method or constructor invocation to throw a
 * {@code ClassCastException}.
 *
 * 该Map中所有的键都必须实现Comparable接口，当然也可以指定一个全局的比较器。
 * 此外，键还必须是自然可比的，也就是不能由于实例类型不同而抛出异常。
 * 不然的话则会在尝试获取值或者构建值的抛出ClassCastException。
 *
 * <p>Note that the ordering maintained by a sorted map (whether or not an
 * explicit comparator is provided) must be <em>consistent with equals</em> if
 * the sorted map is to correctly implement the {@code Map} interface.  (See
 * the {@code Comparable} interface or {@code Comparator} interface for a
 * precise definition of <em>consistent with equals</em>.)  This is so because
 * the {@code Map} interface is defined in terms of the {@code equals}
 * operation, but a sorted map performs all key comparisons using its
 * {@code compareTo} (or {@code compare}) method, so two keys that are
 * deemed equal by this method are, from the standpoint of the sorted map,
 * equal.  The behavior of a tree map <em>is</em> well-defined even if its
 * ordering is inconsistent with equals; it just fails to obey the general
 * contract of the {@code Map} interface.
 *
 * 值得注意的是，一个排序的Map不论是否指定了全局的比较器，维护的顺序必须和Map接口的equals方法保持一致，虽然比较器Comparator没有这样的要求。
 * 具体的可以看Comparator接口中关于对equals的说明。（也就是说compareTo==0的话，equals也返回true）
 * 这是因为Map接口依靠的是hashcode和equals方法来排列元素，而SortMap依靠的是键的compareTo函数来排列元素，所以要求compareTo和equals的结果相同。
 * TreeMap没有遵循这个规则但是也可以运行，只是在某些Map接口函数上无法表现正常。
 * （也就是强烈建议：compare(x,y)==0是x.equals(y)的充分必要条件）
 *
 * <p>All general-purpose sorted map implementation classes should provide four
 * "standard" constructors. It is not possible to enforce this recommendation
 * though as required constructors cannot be specified by interfaces. The
 * expected "standard" constructors for all sorted map implementations are:
 * <ol>
 * <li>A void (no arguments) constructor, which creates an empty sorted map
 * sorted according to the natural ordering of its keys.</li>
 * <li>A constructor with a single argument of type {@code Comparator}, which
 * creates an empty sorted map sorted according to the specified comparator.</li>
 * <li>A constructor with a single argument of type {@code Map}, which creates
 * a new map with the same key-value mappings as its argument, sorted
 * according to the keys' natural ordering.</li>
 * <li>A constructor with a single argument of type {@code SortedMap}, which
 * creates a new sorted map with the same key-value mappings and the same
 * ordering as the input sorted map.</li>
 * </ol>
 *
 * 一般来说，SortMap的实现类应该提供4个构造器，分别如下：
 * 1. 默认的无参构造器
 * 2. 仅指定全局比较器的构造器
 * 3. 仅指定初始化普通Map的构造器
 * 4. 仅指定初始化SortMap的构造器
 *
 * <p><strong>Note</strong>: several methods return submaps with restricted key
 * ranges. Such ranges are <em>half-open</em>, that is, they include their low
 * endpoint but not their high endpoint (where applicable).  If you need a
 * <em>closed range</em> (which includes both endpoints), and the key type
 * allows for calculation of the successor of a given key, merely request
 * the subrange from {@code lowEndpoint} to
 * {@code successor(highEndpoint)}.  For example, suppose that {@code m}
 * is a map whose keys are strings.  The following idiom obtains a view
 * containing all of the key-value mappings in {@code m} whose keys are
 * between {@code low} and {@code high}, inclusive:<pre>
 *   SortedMap&lt;String, V&gt; sub = m.subMap(low, high+"\0");</pre>
 *
 * A similar technique can be used to generate an <em>open range</em>
 * (which contains neither endpoint).  The following idiom obtains a
 * view containing all of the key-value mappings in {@code m} whose keys
 * are between {@code low} and {@code high}, exclusive:<pre>
 *   SortedMap&lt;String, V&gt; sub = m.subMap(low+"\0", high);</pre>
 *
 * 有多个方法可以返回指定范围的Map子集，指定的范围默认是低闭高开区间。
 * 当你需要结尾包含元素toKey时，那么范围的结束值应该写为"toKey+"\0""。
 * 类似的当你想要不包含起始位置fromKey时，那么范围的起始值应该写为"fromKey+"\0""。
 * （起始+"\0"就相当于一个开关，用来调整是否需要包含边界值。
 *
 * <p>This interface is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @param <K>
 * 		the type of keys maintained by this map
 * @param <V>
 * 		the type of mapped values
 *
 * @author Josh Bloch
 * @see Map
 * @see TreeMap
 * @see SortedSet
 * @see Comparator
 * @see Comparable
 * @see Collection
 * @see ClassCastException
 * @since 1.2
 */

public interface SortedMap<K, V> extends Map<K, V> {
	/**
	 * Returns the comparator used to order the keys in this map, or
	 * {@code null} if this map uses the {@linkplain Comparable
	 * natural ordering} of its keys.
	 *
	 * 返回全局的比较器，如果使用的是各键自己实现Comparator来进行排序的，则返回空。
	 *
	 * @return the comparator used to order the keys in this map,
	 * or {@code null} if this map uses the natural ordering
	 * of its keys
	 */
	Comparator<? super K> comparator();

	/**
	 * Returns a view of the portion of this map whose keys range from
	 * {@code fromKey}, inclusive, to {@code toKey}, exclusive.  (If
	 * {@code fromKey} and {@code toKey} are equal, the returned map
	 * is empty.)  The returned map is backed by this map, so changes
	 * in the returned map are reflected in this map, and vice-versa.
	 * The returned map supports all optional map operations that this
	 * map supports.
	 *
	 * 返回[fromKey,toKey)之间的元素
	 *
	 * <p>The returned map will throw an {@code IllegalArgumentException}
	 * on an attempt to insert a key outside its range.
	 *
	 * @param fromKey
	 * 		low endpoint (inclusive) of the keys in the returned map
	 * @param toKey
	 * 		high endpoint (exclusive) of the keys in the returned map
	 *
	 * @return a view of the portion of this map whose keys range from
	 * {@code fromKey}, inclusive, to {@code toKey}, exclusive
	 *
	 * @throws ClassCastException
	 * 		if {@code fromKey} and {@code toKey}
	 * 		cannot be compared to one another using this map's comparator
	 * 		(or, if the map has no comparator, using natural ordering).
	 * 		Implementations may, but are not required to, throw this
	 * 		exception if {@code fromKey} or {@code toKey}
	 * 		cannot be compared to keys currently in the map.
	 * @throws NullPointerException
	 * 		if {@code fromKey} or {@code toKey}
	 * 		is null and this map does not permit null keys
	 * @throws IllegalArgumentException
	 * 		if {@code fromKey} is greater than
	 * 		{@code toKey}; or if this map itself has a restricted
	 * 		range, and {@code fromKey} or {@code toKey} lies
	 * 		outside the bounds of the range
	 */
	SortedMap<K, V> subMap(K fromKey, K toKey);

	/**
	 * Returns a view of the portion of this map whose keys are
	 * strictly less than {@code toKey}.  The returned map is backed
	 * by this map, so changes in the returned map are reflected in
	 * this map, and vice-versa.  The returned map supports all
	 * optional map operations that this map supports.
	 *
	 * <p>The returned map will throw an {@code IllegalArgumentException}
	 * on an attempt to insert a key outside its range.
	 *
	 * 返回[0,toKey)之间的元素
	 *
	 * @param toKey
	 * 		high endpoint (exclusive) of the keys in the returned map
	 *
	 * @return a view of the portion of this map whose keys are strictly
	 * less than {@code toKey}
	 *
	 * @throws ClassCastException
	 * 		if {@code toKey} is not compatible
	 * 		with this map's comparator (or, if the map has no comparator,
	 * 		if {@code toKey} does not implement {@link Comparable}).
	 * 		Implementations may, but are not required to, throw this
	 * 		exception if {@code toKey} cannot be compared to keys
	 * 		currently in the map.
	 * @throws NullPointerException
	 * 		if {@code toKey} is null and
	 * 		this map does not permit null keys
	 * @throws IllegalArgumentException
	 * 		if this map itself has a
	 * 		restricted range, and {@code toKey} lies outside the
	 * 		bounds of the range
	 */
	SortedMap<K, V> headMap(K toKey);

	/**
	 * Returns a view of the portion of this map whose keys are
	 * greater than or equal to {@code fromKey}.  The returned map is
	 * backed by this map, so changes in the returned map are
	 * reflected in this map, and vice-versa.  The returned map
	 * supports all optional map operations that this map supports.
	 *
	 * <p>The returned map will throw an {@code IllegalArgumentException}
	 * on an attempt to insert a key outside its range.
	 *
	 * 返回[fromKey,end]之间的元素
	 *
	 * @param fromKey
	 * 		low endpoint (inclusive) of the keys in the returned map
	 *
	 * @return a view of the portion of this map whose keys are greater
	 * than or equal to {@code fromKey}
	 *
	 * @throws ClassCastException
	 * 		if {@code fromKey} is not compatible
	 * 		with this map's comparator (or, if the map has no comparator,
	 * 		if {@code fromKey} does not implement {@link Comparable}).
	 * 		Implementations may, but are not required to, throw this
	 * 		exception if {@code fromKey} cannot be compared to keys
	 * 		currently in the map.
	 * @throws NullPointerException
	 * 		if {@code fromKey} is null and
	 * 		this map does not permit null keys
	 * @throws IllegalArgumentException
	 * 		if this map itself has a
	 * 		restricted range, and {@code fromKey} lies outside the
	 * 		bounds of the range
	 */
	SortedMap<K, V> tailMap(K fromKey);

	/**
	 * Returns the first (lowest) key currently in this map.
	 *
	 * @return the first (lowest) key currently in this map
	 *
	 * @throws NoSuchElementException
	 * 		if this map is empty
	 */
	K firstKey();

	/**
	 * Returns the last (highest) key currently in this map.
	 *
	 * @return the last (highest) key currently in this map
	 *
	 * @throws NoSuchElementException
	 * 		if this map is empty
	 */
	K lastKey();

	/**
	 * Returns a {@link Set} view of the keys contained in this map.
	 * The set's iterator returns the keys in ascending order.
	 * The set is backed by the map, so changes to the map are
	 * reflected in the set, and vice-versa.  If the map is modified
	 * while an iteration over the set is in progress (except through
	 * the iterator's own {@code remove} operation), the results of
	 * the iteration are undefined.  The set supports element removal,
	 * which removes the corresponding mapping from the map, via the
	 * {@code Iterator.remove}, {@code Set.remove},
	 * {@code removeAll}, {@code retainAll}, and {@code clear}
	 * operations.  It does not support the {@code add} or {@code addAll}
	 * operations.
	 *
	 * @return a set view of the keys contained in this map, sorted in
	 * ascending order
	 */
	Set<K> keySet();

	/**
	 * Returns a {@link Collection} view of the values contained in this map.
	 * The collection's iterator returns the values in ascending order
	 * of the corresponding keys.
	 * The collection is backed by the map, so changes to the map are
	 * reflected in the collection, and vice-versa.  If the map is
	 * modified while an iteration over the collection is in progress
	 * (except through the iterator's own {@code remove} operation),
	 * the results of the iteration are undefined.  The collection
	 * supports element removal, which removes the corresponding
	 * mapping from the map, via the {@code Iterator.remove},
	 * {@code Collection.remove}, {@code removeAll},
	 * {@code retainAll} and {@code clear} operations.  It does not
	 * support the {@code add} or {@code addAll} operations.
	 *
	 * @return a collection view of the values contained in this map,
	 * sorted in ascending key order
	 */
	Collection<V> values();

	/**
	 * Returns a {@link Set} view of the mappings contained in this map.
	 * The set's iterator returns the entries in ascending key order.
	 * The set is backed by the map, so changes to the map are
	 * reflected in the set, and vice-versa.  If the map is modified
	 * while an iteration over the set is in progress (except through
	 * the iterator's own {@code remove} operation, or through the
	 * {@code setValue} operation on a map entry returned by the
	 * iterator) the results of the iteration are undefined.  The set
	 * supports element removal, which removes the corresponding
	 * mapping from the map, via the {@code Iterator.remove},
	 * {@code Set.remove}, {@code removeAll}, {@code retainAll} and
	 * {@code clear} operations.  It does not support the
	 * {@code add} or {@code addAll} operations.
	 *
	 * @return a set view of the mappings contained in this map,
	 * sorted in ascending key order
	 */
	Set<Map.Entry<K, V>> entrySet();
}
