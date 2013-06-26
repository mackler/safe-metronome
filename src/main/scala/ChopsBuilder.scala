package org.mackler.metronome

import scala.concurrent.duration._

//class ChopsBuilder(scheduler: akka.actor.Scheduler)(implicit executionContext: ExecutionContext) {
class ChopsBuilder extends Actor {
  implicit val executionContext = context.system.dispatcher

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

  def receive = {
    case TargetTempo(bpm) ⇒ mTargetTempo = bpm
    case Milliseconds(timeLeft) ⇒ mMillisecondsLeft = timeLeft
    case DataRequest ⇒ if (isOn) sender ! Data(targetTempo, formattedTime)
    case Start(startTempo) ⇒ start(startTempo)
    case Reset ⇒ sender ! Milliseconds(reset())
    case CurrentTempo ⇒ if (isRunning) sender ! currentTempo
    case Pause ⇒ if (isRunning) pause()
    case Adjust(tempo) ⇒ if (isRunning) adjust(tempo)
    case TargetTempo ⇒ if (isRunning) sender ! targetTempo
    case Milliseconds ⇒ if (isRunning) sender ! milliseconds
    case ChopsCancel ⇒ cancel()
    case FormattedTime ⇒ sender ! FormattedTime(formattedTime)

    case _ ⇒ logD("received unknown message")
  }

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

  def targetTempo: Int = synchronized {
    if (isOff) throw new IllegalStateException("ChopsBuilder is not on")
    else mTargetTempo
  }

  def milliseconds: Int = if (isOff) 0 else mMillisecondsLeft

  def formattedTime: String = if (isOff) "0:00" else {
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
     if (mChopsCompleter.isDefined) { mChopsCompleter.get.cancel() }
      mChopsCompleter = Option (
	context.system.scheduler.scheduleOnce(mMillisecondsLeft.milliseconds)(onCompletion)
      )
      true
    } else { // start tempo was too fast, cancel, reset, turn off and return false
      reset()
      mMillisecondsLeft = 0
      false
    }
  }

  private def onCompletion() {
    logD(s"ChopsBuilder™ object's completion function called")
    reset()
    mMillisecondsLeft = 0
    context.parent ! Complete(mTargetTempo)
    logD(s"ChopsBuilder™ object's completion function completed")
  }
}
