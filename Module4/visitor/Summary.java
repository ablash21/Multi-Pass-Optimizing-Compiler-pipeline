package visitor;
import java.util.*;

/**
 * Per-(Method, Context) analysis result.
 *
 *   returnPts : pts of the returned variable at method exit
 *   exitHeap  : sigma at method exit, propagated back to caller(s)
 *
 * Note: we deliberately do NOT store the callee's exit stack (P).  Locals
 * don't escape, so callers can't observe them.  This shrinks summaries and
 * keeps equality comparisons fast.
 */
public final class Summary {

    public final Set<AbsObj> returnPts;
    public final Map<String, Map<String, Set<AbsObj>>> exitHeap;

    public Summary(Set<AbsObj> returnPts,
                   Map<String, Map<String, Set<AbsObj>>> exitHeap) {
        this.returnPts = new HashSet<>(returnPts);
        // Deep-copy the heap to insulate the summary from later sigma mutations.
        this.exitHeap = new HashMap<>();
        for (Map.Entry<String, Map<String, Set<AbsObj>>> oe : exitHeap.entrySet()) {
            Map<String, Set<AbsObj>> inner = new HashMap<>();
            for (Map.Entry<String, Set<AbsObj>> fe : oe.getValue().entrySet()) {
                inner.put(fe.getKey(), new HashSet<>(fe.getValue()));
            }
            this.exitHeap.put(oe.getKey(), inner);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Summary)) return false;
        Summary s = (Summary) o;
        return returnPts.equals(s.returnPts) && exitHeap.equals(s.exitHeap);
    }

    @Override
    public int hashCode() { return returnPts.hashCode() ^ exitHeap.hashCode(); }
}
