package ch.so.agi.hop.interlis.transforms.test;

import org.apache.hop.pipeline.transform.BaseTransformData;

/** Runtime state of the {@link InterlisTest} transform. */
public class InterlisTestData extends BaseTransformData {

  boolean initialized;
  int rowIndex;
}
