package gov.va.research.red;

public class SnippetPosition  implements Comparable<SnippetPosition> {

	public final int start;
	public final int end;
	public SnippetPosition(final int start, final int end) {
		this.start = start;
		this.end = end;
	}
	@Override
	public int hashCode() {
		int result = 17;
		result = 31 * result + start;
		result = 31 * result + end;
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof SnippetPosition)) {
			return false;
		}
		SnippetPosition sp = (SnippetPosition)obj;
		return sp.start == start && sp.end == end;
	}
	@Override
	public String toString() {
		return "" + start + "-" + end;
	}
	@Override
	public int compareTo(SnippetPosition o) {
		if (start < o.start) {
			return -1;
		}
		if (start > o.start){
			return 1;
		}
		if (end < o.end) {
			return -1;
		}
		if (end > o.end){
			return 1;
		}
		
		return 0;
	}
}
