package gov.va.research.red;

public class Position  implements Comparable<Position> {

	public final int start;
	public final int end;
	public Position(final int start, final int end) {
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
		if (!(obj instanceof Position)) {
			return false;
		}
		Position sp = (Position)obj;
		return sp.start == start && sp.end == end;
	}
	@Override
	public String toString() {
		return "" + start + "-" + end;
	}
	@Override
	public int compareTo(Position o) {
		if (start < o.start) {
			return -1;
		}
		if (start > o.start){
			return 1;
		}
		return 0;
	}
	
	public boolean overlaps(Position other) {
		if (
				(this.start >= other.start && this.start <= other.end)
				||
				(this.end >= other.start && this.end <= other.end)
			) {
			return true;
		}
		return false;
	}
}
