# VLS missed-target negative control

`ApocalypseMissile-before.class` is the compiled DNV artillery class from the clean
`ef0f9c00e438a5ef15bba1e5af755d6b7faf84ae` input, before this field-feedback fix.
SHA-256: `ad5cd51546623dc352582561fa38f2eab6acdbe8a82c8c90ee513a6d1fe21d3b`.
It is loaded only by the isolated test fixture with explicit world/entity ports;
it never enters runtime sources or the distributed JAR.
