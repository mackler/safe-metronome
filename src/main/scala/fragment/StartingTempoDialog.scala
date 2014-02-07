package org.mackler.metronome

class StartingTempoDialog extends DialogFragment {
  var mMinutes: Int = 0
  var mStartTempo: Int = MainActor.MIN_TEMPO
  var mMaxTempo: Int = MainActor.MAX_TEMPO

  def minutes = mMinutes

  override def onCreate (savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    val args = if (savedInstanceState != null) savedInstanceState else getArguments
    mMinutes = args.getInt("minutes")
    mStartTempo = args.getInt("startTempo")
    mMaxTempo = args.getInt("maxTempo") // not used anymore; some didn't like limit
  }

  override def onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putInt("minutes",mMinutes)
    outState.putInt(
      "startTempo",
      { /* on Dec 12, 2013 someone got an NPE from either getView or findViewById,
         * which were then both on same line. */
        Option(getView) match {
	  case None => mStartTempo
	  case Some(rootView) =>
	    (Option(rootView.findViewById(R.id.tempo_picker)): @unchecked) match {
	      case None => mStartTempo
	      case Some(numberPicker: NumberPicker) => numberPicker.getValue
	    }
	}
      }
    )
    outState.putInt("maxTempo", mMaxTempo)
  }

  override def onCreateView (
    inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle
  ): View = {
    this.getDialog.setTitle(getString(R.string.set_start_tempo))
    val tempoLayout: View = inflater.inflate(R.layout.start_tempo, container, false)
    val tempoPicker = tempoLayout.findViewById(R.id.tempo_picker).asInstanceOf[NumberPicker]
    tempoPicker.setMinValue(MainActor.MIN_TEMPO)
    tempoPicker.setMaxValue(MainActor.MAX_TEMPO) // used to be mMaxTempo
    tempoPicker.setValue(mStartTempo)
    tempoPicker.setWrapSelectorWheel(false)
    tempoPicker.setOnLongPressUpdateInterval(5)

    val tapButton = tempoLayout.findViewById(R.id.dialog_tap_button).asInstanceOf[View]
    tapButton.setOnTouchListener(getActivity.asInstanceOf[MainActivity].onTapListener)

    val startButton = tempoLayout.findViewById(R.id.start_button).asInstanceOf[Button]
    startButton.setOnClickListener( new android.view.View.OnClickListener {
      def onClick(v: View) {
	getActivity.asInstanceOf[MainActivity].startChopsBuilder(tempoPicker.getValue, mMinutes)
      }
    })

    tempoLayout
  }

}

object StartingTempoDialog { def newInstance(minutes: Int, startTempo: Int, maxTempo: Int) = {
  val f = new StartingTempoDialog
  val args = new Bundle
  args.putInt("minutes", minutes)
  args.putInt("startTempo", startTempo)
  args.putInt("maxTempo", maxTempo)
  f.setArguments(args)
  f
}}
