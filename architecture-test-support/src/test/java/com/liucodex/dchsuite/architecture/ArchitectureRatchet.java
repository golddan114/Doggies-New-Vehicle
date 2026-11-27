package com.liucodex.dchsuite.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Shared, test-only ArchUnit dependency ratchet for the two independent builds.
 * Reports every assigned production-module edge. DCH assignment uses compiled
 * source roots; package rules remain for other scopes. Forbidden/cyclic edges
 * fail above the reviewed allowance (zero for new module names).
 */
public final class ArchitectureRatchet {
    private static final String MAPPING_PATH =
            "architecture-test-support/fixtures/ratchet/MODULE-MAPPING.tsv";
    private static final String BASELINE_DIRECTORY =
            "architecture-test-support/fixtures/ratchet/baselines";

    private ArchitectureRatchet() {
    }

    public static void verify(String scope, String... importedPackages) throws IOException {
        Path workspace = findWorkspaceRoot();
        ScopeMapping mapping = ScopeMapping.load(workspace.resolve(MAPPING_PATH), scope, workspace);
        JavaClasses imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(importedPackages);

        Analysis analysis = analyze(imported, mapping);
        Path reportDirectory = Path.of(
                "build", "reports", "dchsuite-architecture").toAbsolutePath();
        Files.createDirectories(reportDirectory);
        Path report = reportDirectory.resolve(scope + ".txt");
        Files.writeString(report, analysis.render(scope), StandardCharsets.UTF_8);
        Files.writeString(reportDirectory.resolve(scope + "-class-dependencies.tsv"),
                analysis.renderClassDependencies(), StandardCharsets.UTF_8);
        StringBuilder assignments = new StringBuilder("class\tmodule\tbytecode_source\n");
        imported.stream().sorted(Comparator.comparing(JavaClass::getName)).forEach(type -> {
            assignments.append(type.getName()).append('\t')
                    .append(mapping.internalRule(type).orElseThrow().module).append('\t')
                    .append(type.getSource().map(source -> source.getUri().toString()).orElse("MISSING"))
                    .append('\n');
        });
        Files.writeString(reportDirectory.resolve(scope + "-class-assignments.tsv"),
                assignments, StandardCharsets.UTF_8);

        Path baseline = workspace.resolve(BASELINE_DIRECTORY).resolve(scope + ".tsv");
        if (!Files.isRegularFile(baseline)) {
            Path candidate = reportDirectory.resolve(scope + "-baseline-candidate.tsv");
            Files.writeString(candidate, analysis.renderBaseline(), StandardCharsets.UTF_8);
            throw new AssertionError(
                    "Missing reviewed architecture baseline " + baseline
                            + "; candidate written to " + candidate);
        }

        Map<ExceptionKey, Integer> allowances = readBaseline(baseline);
        List<String> increases = new ArrayList<>();
        analysis.exceptions.forEach((key, count) -> {
            int allowed = allowances.getOrDefault(key, 0);
            if (count > allowed) {
                increases.add(key.render() + " current=" + count + " allowed=" + allowed);
            }
        });
        if (!increases.isEmpty()) {
            throw new AssertionError(
                    "Architecture ratchet detected new/increased violations for " + scope
                            + "; report=" + report + "\n - " + String.join("\n - ", increases));
        }
    }

    static Analysis analyze(JavaClasses imported, ScopeMapping mapping) {
        Map<String, Integer> classCounts = new TreeMap<>();
        Map<ModuleEdge, Set<String>> dependencyPairs = new HashMap<>();
        List<String> unassigned = new ArrayList<>();
        Map<String, ModuleRule> importedRules = new HashMap<>();
        for (JavaClass type : imported) {
            mapping.internalRule(type).ifPresent(rule -> importedRules.put(type.getName(), rule));
        }

        for (JavaClass origin : imported) {
            Optional<ModuleRule> originRule = Optional.ofNullable(importedRules.get(origin.getName()));
            if (originRule.isEmpty()) {
                unassigned.add(origin.getName());
                continue;
            }
            classCounts.merge(originRule.get().module, 1, Integer::sum);
            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                Optional<ModuleRule> targetRule = Optional.ofNullable(importedRules.get(target.getName()))
                        .or(() -> mapping.anyRule(target));
                if (targetRule.isEmpty()
                        || originRule.get().module.equals(targetRule.get().module)) {
                    continue;
                }
                ModuleEdge edge = new ModuleEdge(
                        originRule.get().module, targetRule.get().module);
                dependencyPairs.computeIfAbsent(edge, ignored -> new TreeSet<>())
                        .add(origin.getName() + " -> "
                                + dependency.getTargetClass().getName());
            }
        }

        if (!unassigned.isEmpty()) {
            unassigned.sort(String::compareTo);
            throw new AssertionError(
                    "Architecture module mapping has unassigned production classes:\n - "
                            + String.join("\n - ", unassigned));
        }

        Map<ModuleEdge, Integer> edges = new TreeMap<>();
        dependencyPairs.forEach((edge, pairs) -> edges.put(edge, pairs.size()));
        Set<ModuleEdge> cyclicEdges = cyclicEdges(
                edges.keySet(), mapping.cycleModules());
        Map<ExceptionKey, Integer> exceptions = new TreeMap<>();

        edges.forEach((edge, count) -> {
            if (!mapping.allowedTargets(edge.from).contains(edge.to)) {
                exceptions.put(new ExceptionKey("FORBIDDEN_DEPENDENCY", edge.from, edge.to), count);
            }
            if (cyclicEdges.contains(edge)) {
                exceptions.put(new ExceptionKey("CYCLE_EDGE", edge.from, edge.to), count);
            }
        });
        return new Analysis(classCounts, edges, dependencyPairs, cyclicEdges, exceptions, mapping);
    }

    private static Set<ModuleEdge> cyclicEdges(
            Set<ModuleEdge> edges,
            Set<String> modules) {
        Map<String, Set<String>> graph = new HashMap<>();
        modules.forEach(module -> graph.put(module, new LinkedHashSet<>()));
        edges.forEach(edge -> {
            if (modules.contains(edge.from) && modules.contains(edge.to)) {
                graph.get(edge.from).add(edge.to);
            }
        });
        Set<ModuleEdge> result = new TreeSet<>();
        edges.forEach(edge -> {
            if (modules.contains(edge.from)
                    && modules.contains(edge.to)
                    && reachable(edge.to, edge.from, graph)) {
                result.add(edge);
            }
        });
        return result;
    }

    private static boolean reachable(
            String start,
            String expected,
            Map<String, Set<String>> graph) {
        Deque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(expected)) {
                return true;
            }
            pending.addAll(graph.getOrDefault(current, Set.of()));
        }
        return false;
    }

    private static Map<ExceptionKey, Integer> readBaseline(Path baseline) throws IOException {
        Map<ExceptionKey, Integer> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(baseline, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("type\t")) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length != 4) {
                throw new IllegalArgumentException("Invalid architecture baseline row: " + line);
            }
            ExceptionKey key = new ExceptionKey(parts[0], parts[1], parts[2]);
            Integer previous = result.put(key, Integer.parseInt(parts[3]));
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate architecture baseline row: " + line);
            }
        }
        return result;
    }

    private static Path findWorkspaceRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(MAPPING_PATH))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "Cannot locate DCHSuite workspace from " + System.getProperty("user.dir"));
    }

    private record ModuleEdge(String from, String to) implements Comparable<ModuleEdge> {
        @Override
        public int compareTo(ModuleEdge other) {
            int byFrom = from.compareTo(other.from);
            return byFrom != 0 ? byFrom : to.compareTo(other.to);
        }
    }

    record ExceptionKey(String type, String from, String to)
            implements Comparable<ExceptionKey> {
        @Override
        public int compareTo(ExceptionKey other) {
            int byType = type.compareTo(other.type);
            if (byType != 0) {
                return byType;
            }
            int byFrom = from.compareTo(other.from);
            return byFrom != 0 ? byFrom : to.compareTo(other.to);
        }

        String render() {
            return type + " " + from + " -> " + to;
        }
    }

    private record ModuleRule(
            String module,
            boolean internal,
            Set<String> allowedTargets,
            boolean cycleParticipant,
            Pattern pattern,
            String bytecodeRoot) {
        boolean matches(JavaClass type) {
            return pattern.matcher(type.getName()).matches()
                    && (bytecodeRoot == null || type.getSource()
                    .map(source -> source.getUri().toString().startsWith(bytecodeRoot)).orElse(false));
        }
    }

    static final class ScopeMapping {
        private final List<ModuleRule> rules;

        private ScopeMapping(List<ModuleRule> rules) {
            this.rules = rules;
        }

        static ScopeMapping load(Path path, String scope, Path workspace) throws IOException {
            List<ModuleRule> rules = new ArrayList<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("scope\t")) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length < 5 || parts.length > 7) {
                    throw new IllegalArgumentException("Invalid architecture mapping row: " + line);
                }
                if (!scope.equals(parts[0])) {
                    continue;
                }
                Set<String> allowed = parts[3].isBlank()
                        ? Set.of()
                        : Set.of(parts[3].split(","));
                rules.add(new ModuleRule(
                        parts[1], Boolean.parseBoolean(parts[2]), allowed,
                        parts.length == 5 || Boolean.parseBoolean(parts[5]),
                        Pattern.compile(parts[4]),
                        parts.length < 7 || parts[6].isBlank() ? null
                                : workspace.resolve(parts[6]).toAbsolutePath().toUri().toString()));
            }
            if (rules.isEmpty()) {
                throw new IllegalArgumentException("No architecture mappings for scope " + scope);
            }
            return new ScopeMapping(List.copyOf(rules));
        }

        Optional<ModuleRule> internalRule(JavaClass type) {
            return rules.stream()
                    .filter(rule -> rule.internal && rule.matches(type))
                    .findFirst();
        }

        Optional<ModuleRule> anyRule(JavaClass type) {
            return rules.stream()
                    .filter(rule -> rule.matches(type))
                    .findFirst();
        }

        Set<String> internalModules() {
            Set<String> result = new LinkedHashSet<>();
            rules.stream().filter(rule -> rule.internal)
                    .forEach(rule -> result.add(rule.module));
            return result;
        }

        Set<String> cycleModules() {
            Set<String> result = new LinkedHashSet<>();
            rules.stream()
                    .filter(rule -> rule.internal && rule.cycleParticipant)
                    .forEach(rule -> result.add(rule.module));
            return result;
        }

        Set<String> allowedTargets(String module) {
            return rules.stream()
                    .filter(rule -> rule.module.equals(module))
                    .findFirst()
                    .map(ModuleRule::allowedTargets)
                    .orElse(Set.of());
        }
    }

    static final class Analysis {
        private final Map<String, Integer> classCounts;
        private final Map<ModuleEdge, Integer> edges;
        private final Map<ModuleEdge, Set<String>> dependencyPairs;
        private final Set<ModuleEdge> cyclicEdges;
        final Map<ExceptionKey, Integer> exceptions;
        private final ScopeMapping mapping;

        private Analysis(
                Map<String, Integer> classCounts,
                Map<ModuleEdge, Integer> edges,
                Map<ModuleEdge, Set<String>> dependencyPairs,
                Set<ModuleEdge> cyclicEdges,
                Map<ExceptionKey, Integer> exceptions,
                ScopeMapping mapping) {
            this.classCounts = classCounts;
            this.edges = edges;
            this.dependencyPairs = new TreeMap<>(dependencyPairs);
            this.cyclicEdges = cyclicEdges;
            this.exceptions = exceptions;
            this.mapping = mapping;
        }

        String render(String scope) {
            StringBuilder result = new StringBuilder();
            result.append("scope\t").append(scope).append('\n');
            result.append("productionClasses\t")
                    .append(classCounts.values().stream().mapToInt(Integer::intValue).sum())
                    .append('\n');
            result.append("legacyExceptions\t").append(exceptions.size()).append("\n\n");
            result.append("[modules]\nmodule\tclass_count\n");
            mapping.internalModules().stream().sorted().forEach(module -> result
                    .append(module).append('\t').append(classCounts.getOrDefault(module, 0))
                    .append('\n'));
            result.append("\n[dependencies]\nfrom\tto\tclass_pair_count\tdeclared\tcyclic\n");
            edges.forEach((edge, count) -> result.append(edge.from).append('\t')
                    .append(edge.to).append('\t').append(count).append('\t')
                    .append(mapping.allowedTargets(edge.from).contains(edge.to)).append('\t')
                    .append(cyclicEdges.contains(edge)).append('\n'));
            result.append("\n[legacy-exceptions]\n").append(renderBaseline());
            return result.toString();
        }

        String renderClassDependencies() {
            StringBuilder result = new StringBuilder(
                    "from_module\tto_module\torigin\ttarget\tdeclared\tcyclic\n");
            dependencyPairs.forEach((edge, pairs) -> pairs.stream().sorted().forEach(pair -> {
                String[] classes = pair.split(" -> ", 2);
                result.append(edge.from).append('\t').append(edge.to).append('\t')
                        .append(classes[0]).append('\t').append(classes[1]).append('\t')
                        .append(mapping.allowedTargets(edge.from).contains(edge.to)).append('\t')
                        .append(cyclicEdges.contains(edge)).append('\n');
            }));
            return result.toString();
        }

        String renderBaseline() {
            StringBuilder result = new StringBuilder();
            result.append("# Reviewed legacy allowance; lower counts pass, increases fail.\n");
            result.append("type\tfrom\tto\tallowed_count\n");
            exceptions.forEach((key, count) -> result.append(key.type).append('\t')
                    .append(key.from).append('\t').append(key.to).append('\t')
                    .append(count).append('\n'));
            return result.toString();
        }
    }
}
