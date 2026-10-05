package io.github.zancrow321.minecraftygo.engine.ffi;

/** {@code OCG_QueryInfo}: which {@code QUERY_*} fields to fetch for which card or location. */
public record QueryRequest(int flags, int controller, int location, int sequence, int overlaySequence) {
	public static QueryRequest location(int flags, int controller, int location) {
		return new QueryRequest(flags, controller, location, 0, 0);
	}
}
