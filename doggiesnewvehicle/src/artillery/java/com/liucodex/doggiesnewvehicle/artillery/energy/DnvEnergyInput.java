package com.liucodex.doggiesnewvehicle.artillery.energy;

/** Mixin boundary only: a null budget leaves upstream vehicles untouched. */
public interface DnvEnergyInput {
    DnvEnergyInputBudget dnv$energyInputBudget();
}
