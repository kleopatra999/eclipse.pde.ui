package org.eclipse.pde.internal.ui.wizards.templates;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.pde.core.plugin.*;
import org.eclipse.pde.internal.core.*;
import org.eclipse.pde.internal.core.plugin.WorkspacePluginModelBase;
import org.eclipse.pde.internal.ui.*;
import org.eclipse.pde.internal.ui.editor.manifest.ManifestEditor;
import org.eclipse.pde.internal.ui.wizards.project.ProjectStructurePage;
import org.eclipse.pde.ui.*;
import org.eclipse.pde.ui.templates.*;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.*;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.part.ISetSelectionTarget;

public abstract class AbstractNewPluginTemplateWizard
	extends Wizard
	implements IPluginContentWizard {
	private static final String KEY_WTITLE = "PluginCodeGeneratorWizard.title";
	private static final String KEY_WFTITLE = "PluginCodeGeneratorWizard.ftitle";
	private static final String KEY_MISSING = "PluginCodeGeneratorWizard.missing";
	private static final String KEY_FMISSING = "PluginCoreGeneratorWizard.fmissing";

	private IProjectProvider provider;
	private IPluginStructureData structureData;
	private boolean fragment;
	private FirstTemplateWizardPage firstPage;
	private ITemplateSection[] activeSections;

	/**
	 * Creates a new template wizard.
	 */

	public AbstractNewPluginTemplateWizard() {
		super();
		setDialogSettings(PDEPlugin.getDefault().getDialogSettings());
		setDefaultPageImageDescriptor(PDEPluginImages.DESC_DEFCON_WIZ);
		setNeedsProgressMonitor(true);
	}

	/*
	 * @see IPluginContentWizard#init(IProjectProvider, IPluginStructureData, boolean)
	 */
	public void init(
		IProjectProvider provider,
		IPluginStructureData structureData,
		boolean fragment) {
		this.provider = provider;
		this.structureData = structureData;
		this.fragment = fragment;
		setWindowTitle(
			PDEPlugin.getResourceString(fragment ? KEY_WFTITLE : KEY_WTITLE));
	}

	protected abstract ITemplateSection[] getTemplateSections();
	protected abstract void addAdditionalPages();

	public void addPages() {
		// add the mandatory first page
		firstPage = new FirstTemplateWizardPage(provider, structureData, fragment);
		addPage(firstPage);
		addAdditionalPages();
	}

	public boolean performFinish() {
		activeSections = getTemplateSections();
		final FieldData data = firstPage.createFieldData();
		IRunnableWithProgress operation = new WorkspaceModifyOperation() {
			public void execute(IProgressMonitor monitor) {
				try {
					doFinish(data, monitor);
				} catch (CoreException e) {
					PDEPlugin.logException(e);
				} finally {
					monitor.done();
				}
			}
		};
		try {
			getContainer().run(false, true, operation);
		} catch (InvocationTargetException e) {
			PDEPlugin.logException(e);
			return false;
		} catch (InterruptedException e) {
			PDEPlugin.logException(e);
			return false;
		}
		return true;
	}

	private int computeTotalWork() {
		int totalWork = 5;

		for (int i = 0; i < activeSections.length; i++) {
			totalWork += activeSections[i].getNumberOfWorkUnits();
		}
		return totalWork;
	}

	protected void doFinish(FieldData data, IProgressMonitor monitor)
		throws CoreException {
		int totalWork = computeTotalWork();
		monitor.beginTask("Generating content...", totalWork);
		IProject project = provider.getProject();
		ProjectStructurePage.createProject(project, provider, monitor); // one step
		monitor.worked(1);
		ProjectStructurePage.createBuildProperties(project, structureData, monitor);
		monitor.worked(1);
		ArrayList dependencies = getDependencies();
		WorkspacePluginModelBase model =
			firstPage.createPluginManifest(project, data, dependencies, monitor);
		// one step
		monitor.worked(1);
		verifyPluginPath(dependencies);
		setJavaSettings(model, monitor); // one step
		monitor.worked(1);
		executeTemplates(project, model, monitor); // nsteps
		model.save();
		saveTemplateFile(project, monitor); // one step
		monitor.worked(1);

		IFile file = (IFile) model.getUnderlyingResource();
		IWorkbench workbench = PlatformUI.getWorkbench();
		workbench.getEditorRegistry().setDefaultEditor(
			file,
			PDEPlugin.MANIFEST_EDITOR_ID);
		openPluginFile(file);
	}

	private void setJavaSettings(IPluginModelBase model, IProgressMonitor monitor)
		throws CoreException {
		try {
			BuildPathUtil.setBuildPath(model, monitor);
		} catch (JavaModelException e) {
			String message = e.getMessage();
			IStatus status =
				new Status(IStatus.ERROR, PDEPlugin.getPluginId(), IStatus.OK, message, e);
			throw new CoreException(status);
		}
	}

	private ArrayList getDependencies() {
		ArrayList result = new ArrayList();
		IPluginReference[] list = firstPage.getDependencies();
		addDependencies(list, result);
		for (int i = 0; i < activeSections.length; i++) {
			addDependencies(activeSections[i].getDependencies(), result);
		}
		return result;
	}

	private void addDependencies(IPluginReference[] list, ArrayList result) {
		for (int i = 0; i < list.length; i++) {
			IPluginReference reference = list[i];
			if (!result.contains(reference))
				result.add(reference);
		}
	}

	private void verifyPluginPath(ArrayList dependencies) {
		PluginModelManager manager = PDECore.getDefault().getModelManager();
		ArrayList matches = new ArrayList();
		boolean workspaceModels = false;

		for (int i = 0; i < dependencies.size(); i++) {
			IPluginReference ref = (IPluginReference) dependencies.get(i);
			IPluginModelBase model =
				manager.findPlugin(ref.getId(), ref.getVersion(), ref.getMatch());
			if (model != null) {
				if (model.getUnderlyingResource() != null) {
					workspaceModels = true;
					break;
				} else if (model.isEnabled() == false) {
					// disabled external model
					matches.add(model);
				}
			}
		}
		if (!workspaceModels && matches.size() > 0) {
			// enable
			getShell().getDisplay().syncExec(new Runnable () {
				public void run() {
					if (askToEnable()) {
						ExternalModelManager mng = PDECore.getDefault().getExternalModelManager();
						mng.initializeAndStore(true);
					}
				}
			});
		}
	}
	
	private boolean askToEnable() {
		String title = getWindowTitle();
		String message = PDEPlugin.getResourceString(fragment?KEY_FMISSING:KEY_MISSING);
		return MessageDialog.openQuestion(getShell(), title, message);
	}

	private void executeTemplates(
		IProject project,
		IPluginModelBase model,
		IProgressMonitor monitor)
		throws CoreException {
		for (int i = 0; i < activeSections.length; i++) {
			ITemplateSection section = activeSections[i];
			section.execute(project, model, monitor);
		}
	}

	protected void writeTemplateFile(PrintWriter writer) {
		String indent = "   ";
		// open
		writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		writer.println("<form>");
		if (activeSections.length > 0) {
			// add the standard prolog
			writer.println(
				indent + PDEPlugin.getResourceString("ManifestEditor.TemplatePage.intro"));
			// add template section descriptions
			for (int i = 0; i < activeSections.length; i++) {
				ITemplateSection section = activeSections[i];
				String list = "<li style=\"text\" value=\"" + (i + 1) + ".\">";
				writer.println(
					indent
						+ list
						+ "<b>"
						+ section.getLabel()
						+ ".</b>"
						+ section.getDescription()
						+ "</li>");
			}
		}
		// add the standard epilogue
		writer.println(
			indent + PDEPlugin.getResourceString("ManifestEditor.TemplatePage.common"));
		// close
		writer.println("</form>");
	}

	private void saveTemplateFile(IProject project, IProgressMonitor monitor) {
		StringWriter swriter = new StringWriter();
		PrintWriter writer = new PrintWriter(swriter);
		writeTemplateFile(writer);
		writer.flush();
		try {
			swriter.close();
		} catch (IOException e) {
		}
		String contents = swriter.toString();
		IFile file = project.getFile(".template");

		try {
			ByteArrayInputStream stream =
				new ByteArrayInputStream(contents.getBytes("UTF8"));
			if (file.exists()) {
				file.setContents(stream, false, false, null);
			} else {
				file.create(stream, false, null);
			}
			stream.close();
		} catch (CoreException e) {
			PDEPlugin.logException(e);
		} catch (IOException e) {
		}
	}

	public IEditorInput createEditorInput(IFile file) {
		return new TemplateEditorInput(file, ManifestEditor.TEMPLATE_PAGE);
	}

	private void openPluginFile(final IFile file) {
		final IWorkbenchWindow ww = PDEPlugin.getActiveWorkbenchWindow();

		final IWorkbenchPage page = ww.getActivePage();
		if (page == null)
			return;
		Display d = ww.getShell().getDisplay();
		final IWorkbenchPart focusPart = page.getActivePart();
		d.asyncExec(new Runnable() {
			public void run() {
				try {
					String editorId =
						fragment ? PDEPlugin.FRAGMENT_EDITOR_ID : PDEPlugin.MANIFEST_EDITOR_ID;

					if (focusPart instanceof ISetSelectionTarget) {
						ISelection selection = new StructuredSelection(file);
						((ISetSelectionTarget) focusPart).selectReveal(selection);
					}
					IEditorInput input = createEditorInput(file);
					ww.getActivePage().openEditor(input, editorId);
				} catch (PartInitException e) {
					PDEPlugin.logException(e);
				}
			}
		});
	}

}