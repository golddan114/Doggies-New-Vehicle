package com.liucodex.doggiesnewvehicle.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

/** Loads the server-owned crew compatibility catalog from data packs. */
public final class DnvSbwVehicleCrewReloadListener extends SimpleJsonResourceReloadListener {
  public static final String DIRECTORY = "dnv/vehicle_crew_adapters";

  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
  private static final Logger LOGGER = LogUtils.getLogger();

  private final DnvSbwVehicleCrewCatalog catalog;
  private final Runnable catalogChanged;

  public DnvSbwVehicleCrewReloadListener(
      DnvSbwVehicleCrewCatalog catalog, Runnable catalogChanged) {
    super(GSON, DIRECTORY);
    this.catalog = catalog;
    this.catalogChanged = catalogChanged;
  }

  @Override
  protected void apply(
      Map<ResourceLocation, JsonElement> resources,
      ResourceManager resourceManager,
      ProfilerFiller profiler) {
    Map<String, JsonElement> documents = new LinkedHashMap<>();
    resources.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> documents.put(entry.getKey().toString(), entry.getValue()));
    DnvSbwVehicleCrewCatalog.ReloadResult result = catalog.replace(
        documents, catalog.snapshot().revision());
    catalogChanged.run();
    if (result.accepted()) {
      LOGGER.info(
          "Loaded {} DNV SBW vehicle crew adapters at catalog revision {}",
          result.snapshot().entries().size(),
          result.snapshot().revision());
      return;
    }
    for (DnvSbwVehicleCrewCatalog.Issue issue : result.issues()) {
      LOGGER.error(
          "Rejected DNV SBW vehicle crew adapter {} field {}: {}",
          issue.source(),
          issue.field(),
          issue.code());
    }
    LOGGER.error(
        "DNV vehicle crew catalog revision {} is fail-closed with zero active adapters",
        result.snapshot().revision());
  }
}
