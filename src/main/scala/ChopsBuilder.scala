package org.mackler.metronome

import scala.concurrent.duration._

class ChopsBuilder(scheduler: akka.actor.Scheduler)(implicit executionContext: ExecutionContext) {
  /*
   * These variables are used by the ChopsBuilder™ feature
   * mChopsCompleter tells us whether ChopsBuilder™ is running by
   * whether it is None or Some(_).
   * If it's not running, then mTimeLeft tells us whether
   * ChopsBuilder™ is paused or not by whether or not its
   * value is zero.
   */
  private var mChopsCompleter   : Option[Cancellable] = None
  private var mMillisecondsLeft : Int                 = 0
  private var mTargetTempo      : Int                 = 0
  private var mStartTime        : Long                = 0
  private var mStartTempo       : Int                 = 0
  private var mTargetTime       : Long                = 0
  private var mChopsIncrementer : Option[Cancellable] = None
  private var mOnCompletion     : () => Unit          = () => {}

  def isRunning: Boolean = mChopsCompleter match {
    case Some(_) ⇒ true
    case None ⇒ false
  }

  def isPaused: Boolean =
    if (isRunning) false
    else if (mMillisecondsLeft > 0) true
    else false

  def isOn: Boolean = synchronized { isRunning || isPaused }
  def isOff: Boolean = ! isOn

  def setTargetTempo(bpm: Int) { synchronized {
    mTargetTempo = bpm
  }}

  def targetTempo: Int = synchronized {
    if (isOff) throw new IllegalStateException("ChopsBuilder is not on")
    else mTargetTempo
  }

  def setMilliseconds(timeLeft: Int) { synchronized {
    mMillisecondsLeft = timeLeft
  }}

  def milliseconds: Int = synchronized {
    if (isOff) throw new IllegalStateException("ChopsBuilder is not on")
    else mMillisecondsLeft
  }

  def formattedTime: String = synchronized {
    if (isOff) throw new IllegalStateException("ChopsBuilder is not on")
    val timeLeftMillis = if (isPaused) mMillisecondsLeft
                         else (mTargetTime - System.currentTimeMillis)
    val timeLeftSeconds = (timeLeftMillis / 1000.0).round.toInt
    val displayMinutes = timeLeftSeconds / 60
    val displaySeconds = timeLeftSeconds % 60
    s"$displayMinutes:${displaySeconds.formatted("%02d")}"
  }

  def pause() {
    if (isOff) throw new IllegalStateException("ChopsBuilder is not on")
    else reset()
  }

  /** Doesn't stop running, but resets the starting tempo */
  def adjust(tempo: Int) { synchronized {
    mStartTime = System.currentTimeMillis
    mStartTempo = tempo
  }}

  def currentTempo: Float =
    if (isOff) throw new IllegalStateException("ChopsBuilder is not on")
    else {
      val runTime = mTargetTime - mStartTime
      val tempoRange = mTargetTempo - mStartTempo
      val portionCompleted: Float = (System.currentTimeMillis - mStartTime).toFloat / runTime
      (tempoRange * portionCompleted) + mStartTempo
    }

  /* Removes all schedulers and returns time left in milliseconds. */
  def reset(): Int = {
     if (mChopsCompleter.isDefined) {
      mChopsCompleter.get.cancel()
      mMillisecondsLeft = (mTargetTime - System.currentTimeMillis).toInt
      mChopsCompleter = None
    }
    mMillisecondsLeft
  }

  /** Turns it off completely (that is, doesn't just pause it) */
  def cancel() {
    reset()
    mMillisecondsLeft = 0
  }

  def start(startTempo: Int): Boolean = synchronized {
    logD(s"ChopBuilder's start() called, start $startTempo BPM, target $mTargetTempo BPM; time $mMillisecondsLeft MS")
    if (startTempo < mTargetTempo) {
      mStartTime = System.currentTimeMillis
      mStartTempo = startTempo
      mTargetTime = mStartTime + mMillisecondsLeft
      logD(s"starttime $mStartTime, targetTime $mTargetTime")
     if (mChopsCompleter.isDefined) { mChopsCompleter.get.cancel() }
      mChopsCompleter = Option (
	scheduler.scheduleOnce(mMillisecondsLeft.milliseconds)(wrappedCompletion(mOnCompletion))
      )
      true
    } else { // start tempo was too fast, cancel, reset, turn off and return false
      reset()
      mMillisecondsLeft = 0
      false
    }
  }

  def setCompletionFunction(f: Function0[Unit]) {
      mOnCompletion = f
  }

  /** Wrapped combination of the function the client wants called and the code
   * this object wants called upon completion */
  private def wrappedCompletion(completionFunction: => Unit): () => Unit = {
    logD(s"ChopsBuilder™ object's complition function called")
    reset()
    mMillisecondsLeft = 0
    mOnCompletion
  }
}
