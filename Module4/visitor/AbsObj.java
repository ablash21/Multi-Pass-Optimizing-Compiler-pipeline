package visitor;

/**
 * An abstract object: one per allocation site in the source.
 *
 * Identity is the {@code siteLabel} ("ClassName@lineNum").  Two AbsObj instances
 * for the same allocation site are .equals() — critical for fixpoint to ever
 * be reached (sets would otherwise grow forever).
 *
 * The {@code cls} field is denormalized: it's encoded in the label already, but
 * stored explicitly to keep virtual-dispatch lookups O(1).
 */
public class AbsObj {
    public final String siteLabel;     // "Cat@23"
    public final String cls;           // "Cat"

    public AbsObj(String siteLabel, String cls) {
        this.siteLabel = siteLabel;
        this.cls = cls;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbsObj)) return false;
        return siteLabel.equals(((AbsObj) o).siteLabel);
    }

    @Override
    public int hashCode() { return siteLabel.hashCode(); }

    @Override
    public String toString() { return siteLabel; }
}
