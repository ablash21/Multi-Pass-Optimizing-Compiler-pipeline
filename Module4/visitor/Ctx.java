package visitor;
import java.util.*;

/**
 * Call context: snapshot of how a method is entered.
 *
 *   thisPts    : P(this)        at entry (which abstract objects is the receiver?)
 *   formalPts  : formalName -> P(formal) at entry
 *
 * Two MethodCtx entries with equal Ctx share a single analysis instance,
 * sharing inSigma and Summary.  Distinct Ctx -> distinct analyses
 * (context-sensitivity).
 *
 * equals/hashCode are content-based — critical for use as a hash key.
 */
public final class Ctx {

    public final Set<AbsObj> thisPts;
    public final Map<String, Set<AbsObj>> formalPts;

    public Ctx() {
        this.thisPts = new HashSet<>();
        this.formalPts = new HashMap<>();
    }

    public Ctx(Set<AbsObj> thisPts, Map<String, Set<AbsObj>> formalPts) {
        this.thisPts = new HashSet<>(thisPts);
        this.formalPts = new HashMap<>();
        for (Map.Entry<String, Set<AbsObj>> e : formalPts.entrySet()) {
            this.formalPts.put(e.getKey(), new HashSet<>(e.getValue()));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ctx)) return false;
        Ctx c = (Ctx) o;
        return thisPts.equals(c.thisPts) && formalPts.equals(c.formalPts);
    }

    @Override
    public int hashCode() { return thisPts.hashCode() ^ formalPts.hashCode(); }

    @Override
    public String toString() { return "this=" + thisPts + " formals=" + formalPts; }
}
