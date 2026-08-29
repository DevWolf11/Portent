package dev.portent.scan;

import dev.portent.Namespaces;
import dev.portent.index.ApiFlags;
import dev.portent.index.ApiIndex;
import dev.portent.index.IndexCoverage;
import dev.portent.model.CallSite;
import dev.portent.model.Finding;
import dev.portent.model.FindingType;
import dev.portent.model.MemberKind;
import dev.portent.model.Severity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns the raw output of scanning a plugin's classes into findings.
 *
 * <p>Every rule here is biased the same way: when the target metadata or the hierarchy is not
 * complete enough to be sure, the severity drops or the finding disappears. A tool that cries wolf
 * gets uninstalled.
 */
public final class PluginAnalyser {

    /** From 26.1 Paper ships unobfuscated and has no remapper, so stamped packages are gone. */
    private static final int[] UNMAPPED_FROM = {26, 1};

    private final ApiIndex index;
    private final IndexCoverage coverage;
    private final MemberResolver resolver;

    public PluginAnalyser(ApiIndex index) {
        this.index = index;
        this.coverage = new IndexCoverage(index);
        this.resolver = new MemberResolver(index);
    }

    /** Accumulates evidence across all of a plugin's classes, then produces the findings. */
    public static final class Collector {
        private final Map<SymbolKey, Set<CallSite>> absent = new LinkedHashMap<>();
        private final Map<FlaggedKey, Set<CallSite>> flagged = new LinkedHashMap<>();
        private final Map<SymbolKey, MemberLookup> decided = new LinkedHashMap<>();
        private final Map<String, Set<CallSite>> internals = new LinkedHashMap<>();
        private final Map<String, Set<CallSite>> worldPaths = new LinkedHashMap<>();
        private final Map<Integer, Set<CallSite>> classVersions = new LinkedHashMap<>();
        private final Map<String, Set<CallSite>> missingTypes = new LinkedHashMap<>();
    }

    private record SymbolKey(MemberKind kind, String owner, String name, String descriptor) {}

    private record FlaggedKey(FindingType type, SymbolKey symbol) {}

    /**
     * Folds one class's scan into the collector.
     *
     * @param checkClassVersion false for multi-release entries, which are only loaded by a JVM that
     *     already supports them and so are not evidence of anything
     */
    public void accept(Collector collector, ClassScan scan, CallSite classSite, boolean checkClassVersion) {
        // A type that is gone takes all of its members with it. Reporting the type once says the
        // same thing as reporting forty absent methods, and says it in a way an admin can act on.
        for (String apiType : scan.apiTypes()) {
            if (index.type(apiType) == null && coverage.covers(apiType)) {
                collector.missingTypes.computeIfAbsent(apiType, k -> new TreeSet<>()).add(classSite);
            }
        }

        for (SymbolReference reference : scan.references()) {
            if (collector.missingTypes.containsKey(reference.owner())) {
                continue;
            }
            SymbolKey key =
                    new SymbolKey(
                            reference.kind(), reference.owner(), reference.name(), reference.descriptor());
            MemberLookup lookup = collector.decided.computeIfAbsent(key, k -> resolve(reference));

            if (lookup.resolution() == Resolution.ABSENT) {
                collector.absent.computeIfAbsent(key, k -> new TreeSet<>()).add(reference.callSite());
            } else if (lookup.resolution() == Resolution.PRESENT && lookup.flags() != null) {
                for (FindingType type : flagTypes(lookup)) {
                    collector
                            .flagged
                            .computeIfAbsent(new FlaggedKey(type, key), k -> new TreeSet<>())
                            .add(reference.callSite());
                }
            }
        }

        for (String internal : scan.internalTypes()) {
            collector.internals.computeIfAbsent(internal, k -> new TreeSet<>()).add(classSite);
        }

        for (ClassScan.StringConstant constant : scan.constants()) {
            if (WorldPaths.describe(constant.value()) != null) {
                collector
                        .worldPaths
                        .computeIfAbsent(constant.value(), k -> new TreeSet<>())
                        .add(constant.site());
            }
        }

        if (checkClassVersion && index.javaVersion() > 0) {
            int required = scan.classFileMajor() - 44;
            if (required > index.javaVersion()) {
                collector
                        .classVersions
                        .computeIfAbsent(scan.classFileMajor(), k -> new TreeSet<>())
                        .add(classSite);
            }
        }
    }

    /** Produces the findings, in a stable order, most severe first. */
    public List<Finding> findings(Collector collector) {
        List<Finding> findings = new ArrayList<>();

        collector.missingTypes.forEach(
                (type, sites) -> findings.add(Finding.missingClass(type, List.copyOf(sites))));

        collector.absent.forEach(
                (key, sites) ->
                        findings.add(
                                Finding.missingMember(
                                        key.kind(), key.owner(), key.name(), key.descriptor(),
                                        List.copyOf(sites))));

        collector.flagged.forEach(
                (key, sites) ->
                        findings.add(
                                Finding.flaggedMember(
                                        key.type(),
                                        key.symbol().kind(),
                                        key.symbol().owner(),
                                        key.symbol().name(),
                                        key.symbol().descriptor(),
                                        detailFor(key.type()),
                                        List.copyOf(sites))));

        collector.internals.forEach(
                (type, sites) -> findings.add(internalsFinding(type, List.copyOf(sites))));

        collector.worldPaths.forEach(
                (constant, sites) ->
                        findings.add(
                                Finding.worldPath(
                                        constant,
                                        WorldPaths.describe(constant)
                                                + "; 26.1 stores worlds under"
                                                + " world/dimensions/minecraft/<dim>/",
                                        List.copyOf(sites))));

        collector.classVersions.forEach(
                (major, sites) ->
                        findings.add(
                                Finding.unsupportedClassVersion(
                                        major, index.javaVersion(), List.copyOf(sites))));

        findings.sort(
                Comparator.comparing(Finding::severity)
                        .thenComparing(Finding::type)
                        .thenComparing(Finding::subject));
        return findings;
    }

    /**
     * A version-stamped internals package is definitively gone from 26.1. An unstamped one still
     * exists on an unobfuscated server, so it is only ever a warning about fragility.
     */
    private Finding internalsFinding(String type, List<CallSite> sites) {
        boolean stamped = Namespaces.isVersionStamped(type);
        if (stamped && index.targetIsAtLeast(UNMAPPED_FROM)) {
            return Finding.internals(
                    FindingType.LEGACY_NMS,
                    Severity.ERROR,
                    type,
                    "version-stamped internals; "
                            + index.minecraftVersion()
                            + " ships unobfuscated and Paper removed its remapper, so this package"
                            + " does not exist",
                    sites);
        }
        if (stamped) {
            return Finding.internals(
                    FindingType.LEGACY_NMS,
                    Severity.WARN,
                    type,
                    index.minecraftVersion() == null
                            ? "version-stamped internals; the index records no target version, so this"
                                    + " is not escalated"
                            : "version-stamped internals, fatal from 26.1",
                    sites);
        }
        return Finding.internals(
                FindingType.SERVER_INTERNALS,
                Severity.WARN,
                type,
                "server internals rather than API; not covered by the index and free to change"
                        + " between builds",
                sites);
    }

    private static List<FindingType> flagTypes(MemberLookup lookup) {
        List<FindingType> types = new ArrayList<>(2);
        if (lookup.has(ApiFlags.FOR_REMOVAL)) {
            types.add(FindingType.DEPRECATED_FOR_REMOVAL);
        } else if (lookup.has(ApiFlags.DEPRECATED)) {
            types.add(FindingType.DEPRECATED_MEMBER);
        }
        if (lookup.has(ApiFlags.INTERNAL)) {
            types.add(FindingType.INTERNAL_API);
        }
        if (lookup.has(ApiFlags.EXPERIMENTAL)) {
            types.add(FindingType.EXPERIMENTAL_API);
        }
        return types;
    }

    private static String detailFor(FindingType type) {
        return switch (type) {
            case DEPRECATED_FOR_REMOVAL -> "marked for removal; it still exists today but will not";
            case DEPRECATED_MEMBER -> "deprecated on the target version";
            case INTERNAL_API -> "@ApiStatus.Internal; not meant for plugins and may change without notice";
            case EXPERIMENTAL_API -> "@ApiStatus.Experimental; may change without notice";
            default -> null;
        };
    }

    private MemberLookup resolve(SymbolReference reference) {
        return reference.kind() == MemberKind.METHOD
                ? resolver.resolveMethod(reference.owner(), reference.name(), reference.descriptor())
                : resolver.resolveField(reference.owner(), reference.name(), reference.descriptor());
    }
}
