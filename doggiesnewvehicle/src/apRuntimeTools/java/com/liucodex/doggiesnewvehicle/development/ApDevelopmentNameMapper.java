package com.liucodex.doggiesnewvehicle.development;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/** Converts explicit SRG member calls/selectors in a development copy, never production classes. */
public final class ApDevelopmentNameMapper {
  private static final Pattern MEMBER = Pattern.compile("\\b[mf]_\\d+_\\b");

  public static void main(String[] args) throws Exception {
    if (args.length != 3) throw new IllegalArgumentException("Expected input, development output and mapping paths");
    Path input = Path.of(args[0]).toAbsolutePath().normalize();
    Path output = Path.of(args[1]).toAbsolutePath().normalize();
    boolean mainRuntime = input.getFileName().toString().equals("main")
        && input.getParent().resolve("apRuntimeBase").equals(output);
    boolean platformRuntime = input.getFileName().toString().equals("platform")
        && input.getParent().resolve("apRuntimePlatform").equals(output);
    boolean artilleryRuntime = input.getFileName().toString().equals("artillery")
        && input.getParent().resolve("apRuntimeArtillery").equals(output);
    boolean bossRuntime = input.getFileName().toString().equals("boss")
        && input.getParent().resolve("apRuntimeBoss").equals(output);
    if (!mainRuntime && !artilleryRuntime && !platformRuntime && !bossRuntime) {
      throw new IllegalArgumentException("Development output must be a distinct approved runtime directory");
    }
    if (Files.exists(output)) {
      try (var staged = Files.walk(output)) {
        for (Path old : staged.filter(Files::isRegularFile).toList()) {
          if (!Files.isRegularFile(input.resolve(output.relativize(old)))) {
            throw new IllegalStateException("Stale development class requires explicit review: " + old);
          }
        }
      }
    }
    Map<String, String> names = new HashMap<>();
    for (String line : Files.readAllLines(Path.of(args[2]))) {
      if (!line.startsWith("\t") || line.startsWith("\t\t")) continue;
      String[] parts = line.trim().split("\\s+");
      if (!MEMBER.matcher(parts[0]).matches()) continue;
      String mapped = parts[parts.length - 1];
      String previous = names.putIfAbsent(parts[0], mapped);
      if (previous != null && !previous.equals(mapped)) throw new IllegalStateException("Ambiguous SRG member " + parts[0]);
    }
    if (!"addFreshEntity".equals(names.get("m_7967_"))) throw new IllegalStateException("Wrong Minecraft mapping input");
    Remapper remapper = new Remapper() {
      private String member(String name) {
        if (MEMBER.matcher(name).matches() && !names.containsKey(name)) {
          throw new IllegalStateException("Unmapped SRG member " + name);
        }
        return names.getOrDefault(name, name);
      }
      @Override public String mapMethodName(String owner, String name, String descriptor) {
        return member(name);
      }
      @Override public String mapFieldName(String owner, String name, String descriptor) {
        return member(name);
      }
      @Override public String mapInvokeDynamicMethodName(String name, String descriptor) {
        return member(name);
      }
      @Override public Object mapValue(Object value) {
        if (!(value instanceof String text)) return super.mapValue(value);
        return MEMBER.matcher(text).replaceAll(match -> member(match.group()));
      }
    };
    int count = 0;
    try (var files = Files.walk(input)) {
      for (Path source : files.filter(Files::isRegularFile).toList()) {
        Path target = output.resolve(input.relativize(source));
        Files.createDirectories(target.getParent());
        byte[] bytes = Files.readAllBytes(source);
        if (source.toString().endsWith(".class")) {
          ClassWriter writer = new ClassWriter(0);
          new ClassReader(bytes).accept(new ClassRemapper(writer, remapper), 0);
          bytes = writer.toByteArray();
          count++;
        }
        Files.write(target, bytes);
      }
    }
    System.out.println("AP_DEV_MAPPING classes=" + count + " members=" + names.size());
  }
}
