# INTERLIS test fixtures

The versioned fixture directories make the transfer version explicit:

```text
fixtures/
├── models/2.3/       # INTERLIS 2.3 model sources
├── models/2.4/       # INTERLIS 2.4 model sources
├── models/repository # offline repository fixtures
├── data/2.3/         # XTF 2.3 transfers
└── data/2.4/         # XTF 2.4 transfers
```

`data/2.3/HopIli_Geometry_V1_*.xtf` intentionally uses the 2.4
`HopIli_Geometry_V1` model. It is the cross-version regression case proving
that the reader continues to accept XTF 2.3 while the model is INTERLIS 2.4.

The test resource helpers expose both version-specific directories and a
temporary aggregate directory for older tests that need to search all local
models.
