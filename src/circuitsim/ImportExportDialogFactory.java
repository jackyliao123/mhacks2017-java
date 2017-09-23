package circuitsim;

public class ImportExportDialogFactory
{
    public static ImportExportDialog Create(CirSim f,
	ImportExportDialog.Action type)
    {
	if (f.applet != null)
	{
/*
	    try
	    {
		return new ImportExportAppletDialog(f, type);
	    }
	    catch (Exception e)
	    {
		return new circuitsim.ImportExportClipboardDialog(f, type);
	    }
*/
		return new ImportExportClipboardDialog(f, type);
	}
	else
	{
	    return new ImportExportFileDialog(f, type);
	}
    }
}
