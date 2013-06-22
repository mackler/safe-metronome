package org.mackler.metronome

class SoundFragment extends DialogFragment {
  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    (new AlertDialogBuilder(getActivity())).
    setTitle(R.string.choose_sound).
    setItems(R.array.sound_names, new DialogOnClickListener() {
      def onClick(dialog: DialogInterface, which: Int) {
	mainActor ! SetSound(which)
      }
    }).
    create()
  }
}

class AboutFragment extends DialogFragment {
  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    (new AlertDialogBuilder(getActivity())).
    setTitle(R.string.app_name).
    setMessage("By Adam Mackler").
    setPositiveButton("OK", new DialogOnClickListener() {
      def onClick(dialog: DialogInterface, which: Int) {}
    }).
    create()
  }
}

class CountdownFragment extends DialogFragment {
  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val minutePicker = new android.widget.NumberPicker(getActivity)
    minutePicker.setMinValue(1)
    minutePicker.setMaxValue(60)
    minutePicker.setWrapSelectorWheel(false)

    (new AlertDialogBuilder(getActivity())).
    setTitle(R.string.set_countdown).
    setPositiveButton(R.string.next, new DialogOnClickListener() {
      def onClick(dialog: DialogInterface, which: Int) {
	getActivity.asInstanceOf[MainActivity].showStartDialog(minutePicker.getValue)
      }
    }).
    setNegativeButton(R.string.cancel, new DialogOnClickListener() {
      def onClick(dialog: DialogInterface, which: Int) {}
    }).
    setView(minutePicker).
    create()
  }
}

class StartTempoFragment(max: Int, val countDown: Int) extends DialogFragment {
  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    logD(s"creating start tempo dialog, count down $countDown minutes")

    val tempoLayout = getActivity.getLayoutInflater.inflate(R.layout.start_tempo, null)
    val tempoPicker = tempoLayout.findViewById(R.id.tempo_picker).asInstanceOf[android.widget.NumberPicker]
    tempoPicker.setMinValue(32)
    tempoPicker.setMaxValue(max)
    tempoPicker.setValue((max-32)/2)
    tempoPicker.setWrapSelectorWheel(false)

    (new AlertDialogBuilder(getActivity)).
    setTitle(R.string.set_start_tempo).
    setPositiveButton(R.string.start, new DialogOnClickListener() {
      def onClick(dialog: DialogInterface, which: Int) {
	getActivity.asInstanceOf[MainActivity].startChopsBuilder(tempoPicker.getValue, countDown)
      }
    }).
    setNegativeButton(R.string.cancel, new DialogOnClickListener() {
      def onClick(dialog: DialogInterface, which: Int) {}
    }).
    setView(tempoLayout).
    create()
  }
}
