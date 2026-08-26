package ch.so.agi.hop.interlis.transforms.transferinput;

import org.apache.hop.core.annotations.Transform;

/**
 * Modes of INTERLIS Transfer Input.
 *
 * <ul>
 *   <li>{@link #OBJECTS}: only object rows, with transfer/basket context in the envelope fields;
 *   <li>{@link #EVENTS}: the exact IOX event stream (START_TRANSFER, START_BASKET, OBJECT,
 *       END_BASKET, END_TRANSFER) for diagnostics and lossless transfer reproduction.
 * </ul>
 */
public enum TransferInputMode {
  OBJECTS,
  EVENTS
}
