package ca.hld.covertart.core

/**
 * Platform-agnostic handle to a source cover image. Pure logic only ever needs
 * its dimensions; executors downcast to their concrete subtype to read pixels.
 */
interface SourceImage {
    val width: Int
    val height: Int
}
