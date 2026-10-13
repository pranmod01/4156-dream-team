package com.resourceallocator.model;

/**
 * Kind of compute resource that can be registered or requested.
 */
public enum ResourceType {
    /** A CPU machine, described by its memory and core count. */
    CPU,
    /** A GPU device, described by its memory. */
    GPU
}
