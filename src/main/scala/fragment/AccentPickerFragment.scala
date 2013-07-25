package org.mackler.metronome

class AccentPickerFragment extends DialogFragment {
  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    (new AlertDialogBuilder(getActivity)).
    setTitle(R.string.beats_per_measure).
    setItems(R.array.time_signatures, new DialogOnClickListener() {
      def onClick(dialog: DialogInterface, which: Int) {
	val activity = getActivity.asInstanceOf[MainActivity]
	activity.runOnUiThread(new Runnable { def run { activity.setAccent(which) }})
      }
    }).
    create()
  }
}
