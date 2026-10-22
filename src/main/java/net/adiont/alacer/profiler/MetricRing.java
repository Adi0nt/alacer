package net.adiont.alacer.profiler;

/**
 * Fixed-capacity, column-oriented ring buffer of {@code long} samples.
 *
 * <p>Each row is one frame (or tick); each column one metric. Storage is allocated once,
 * so recording a row never allocates. Not thread-safe: owned by the thread that records.
 */
public final class MetricRing {

	private final long[][] columns;
	private final int capacity;
	private int next;
	private int size;
	private long totalRecorded;

	public MetricRing(int columns, int capacity) {
		if (columns <= 0 || capacity <= 0) {
			throw new IllegalArgumentException("columns and capacity must be positive");
		}
		this.columns = new long[columns][capacity];
		this.capacity = capacity;
	}

	/** Starts a new row and returns its index for use with {@link #set}. */
	public int append() {
		int row = this.next;
		this.next = row + 1 == this.capacity ? 0 : row + 1;
		if (this.size < this.capacity) {
			this.size++;
		}
		this.totalRecorded++;
		for (long[] column : this.columns) {
			column[row] = 0L;
		}
		return row;
	}

	public void set(int row, int column, long value) {
		this.columns[column][row] = value;
	}

	public long get(int row, int column) {
		return this.columns[column][row];
	}

	public int size() {
		return this.size;
	}

	public int capacity() {
		return this.capacity;
	}

	public int columnCount() {
		return this.columns.length;
	}

	/** Rows recorded since creation or the last {@link #clear()}, including overwritten ones. */
	public long totalRecorded() {
		return this.totalRecorded;
	}

	public void clear() {
		this.next = 0;
		this.size = 0;
		this.totalRecorded = 0;
	}

	/**
	 * Copies the most recent {@code count} values of a column, oldest first, into {@code dest}.
	 * Returns the number of values copied.
	 */
	public int copyRecent(int column, int count, long[] dest) {
		int n = Math.min(Math.min(count, this.size), dest.length);
		int start = this.next - n;
		if (start < 0) {
			start += this.capacity;
		}
		long[] src = this.columns[column];
		int firstPart = Math.min(n, this.capacity - start);
		System.arraycopy(src, start, dest, 0, firstPart);
		if (firstPart < n) {
			System.arraycopy(src, 0, dest, firstPart, n - firstPart);
		}
		return n;
	}
}
