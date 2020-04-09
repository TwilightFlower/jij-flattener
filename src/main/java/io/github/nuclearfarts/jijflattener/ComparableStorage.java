package io.github.nuclearfarts.jijflattener;

public class ComparableStorage<T extends Comparable<T>> {
	private T comparable;
	
	public boolean set(T to) {
		if(comparable == null || to.compareTo(comparable) >= 0) {
			comparable = to;
			return true;
		} else {
			return false;
		}
	}
	
	public T get() {
		return comparable;
	}
}
