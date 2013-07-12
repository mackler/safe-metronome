package org.mackler.metronome

class AboutFragment extends DialogFragment {
  import AboutFragment._

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    (new AlertDialogBuilder(getActivity())).
    setTitle(R.string.app_name).
    setMessage(content).
    setNeutralButton(R.string.feedback, new DialogOnClickListener() {
      def onClick(dialog: DialogInterface, which: Int) {
	val intent = new Intent(ACTION_SEND)
	intent.putExtra(EXTRA_EMAIL, Array("AdamMackler@gmail.com"))
	intent.putExtra(EXTRA_SUBJECT, "Feedback about Safe Metronome")
        intent.setType("message/rfc822")
	startActivity(android.content.Intent.createChooser(intent,"Sending Feedback by Email"))
      }
    }).
    setPositiveButton(R.string.sources, new DialogOnClickListener() {
      def onClick(dialog: DialogInterface, which: Int) { startActivity (
	new Intent(ACTION_VIEW, android.net.Uri.parse("https://bitbucket.org/mackler/safe-metronome"))
      ) }
    }).
//    setPositiveButton(R.string.ok, new DialogOnClickListener() {
//      def onClick(dialog: DialogInterface, which: Int) {}
//    }).
    create()
  }
}

object AboutFragment {
  val content = """|version 0.1.0
                   |By Adam Mackler
                   |
                   |Credits:
                   |Clave by BoilingSand
                   |Cowbell by Neotone
                   |Boxing bell by Benboncan
                   |Launch icon by Cem / cemagraphics
                   |Start icon by Peter Schwarz
                   |Stop icon by Renesis, Silsor & Ed
                   |Tap icon courtesy Nathan Eady
                   |Vertical seek bar coded by Paul Tsupikoff, Fatal1ty2787 & Ramesh
                   |
                   |For contributor contact info, see source repository README.""".stripMargin
}
