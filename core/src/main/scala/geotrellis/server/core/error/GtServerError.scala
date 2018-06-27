package geotrellis.server.core.error


// Errors to be thrown in IO context so that an `attempt` can be
//  done to reason about/react to exceptional state
trait GtServerError extends Exception

// Requirement for some computation does not exist (404 later)
case class RequirementNotFound(message: String) extends GtServerError
