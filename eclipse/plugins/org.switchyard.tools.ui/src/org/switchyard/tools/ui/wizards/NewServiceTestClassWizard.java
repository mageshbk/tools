/* 
 * JBoss, Home of Professional Open Source 
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved. 
 * See the copyright.txt in the distribution for a 
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use, 
 * modify, copy, or redistribute it subject to the terms and conditions 
 * of the GNU Lesser General Public License, v. 2.1. 
 * This program is distributed in the hope that it will be useful, but WITHOUT A 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details. 
 * You should have received a copy of the GNU Lesser General Public License, 
 * v.2.1 along with this distribution; if not, write to the Free Software 
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, 
 * MA  02110-1301, USA.
 */
package org.switchyard.tools.ui.wizards;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.ide.undo.WorkspaceUndoUtil;
import org.eclipse.ui.wizards.newresource.BasicNewResourceWizard;
import org.switchyard.tools.ui.Activator;
import org.switchyard.tools.ui.operations.CreateServiceTestOperation;

/**
 * NewServiceTestClassWizard.
 * 
 * Creates a new service test class.
 * 
 * @author Rob Cernich
 */
public class NewServiceTestClassWizard extends BasicNewResourceWizard {

    private NewServiceTestClassWizardPage _newClassPage;

    /**
     * Create a new NewServiceTestClassWizard.
     */
    public NewServiceTestClassWizard() {
        setNeedsProgressMonitor(true);
    }

    @Override
    public void addPages() {
        _newClassPage = new NewServiceTestClassWizardPage();
        _newClassPage.init(selection);
        addPage(_newClassPage);
    }

    @Override
    public boolean performFinish() {
        final CreateServiceTestOperation op = new CreateServiceTestOperation(_newClassPage,
                WorkspaceUndoUtil.getUIInfoAdapter(getShell()));
        try {
            getContainer().run(true, true, new IRunnableWithProgress() {
                @Override
                public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                    try {
                        ResourcesPlugin.getWorkspace().run(op, monitor);
                    } catch (CoreException e) {
                        throw new InvocationTargetException(e);
                    }
                }
            });
        } catch (InvocationTargetException e) {
            Throwable realException = e.getTargetException();
            if (realException instanceof CoreException) {
                Activator.getDefault().getLog().log(((CoreException) realException).getStatus());
            } else {
                Activator
                        .getDefault()
                        .getLog()
                        .log(new Status(Status.ERROR, Activator.PLUGIN_ID, "Error creating bean service class.",
                                realException));
            }
            MessageDialog.openError(getShell(), "Error Creating Bean Service", realException.getMessage());
            if (!_newClassPage.getModifiedResource().exists()) {
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }

        // reveal and open the file
        final IResource resource = _newClassPage.getModifiedResource();
        if (resource instanceof IFile && resource.exists()) {
            selectAndReveal(resource);
            final IWorkbenchPage activePage = getWorkbench().getActiveWorkbenchWindow().getActivePage();
            if (activePage != null) {
                getShell().getDisplay().asyncExec(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            IDE.openEditor(activePage, (IFile) resource, true);
                        } catch (PartInitException e) {
                            Activator
                                    .getDefault()
                                    .getLog()
                                    .log(new Status(Status.ERROR, Activator.PLUGIN_ID,
                                            "Error opening bean service source.", e));
                        }
                    }
                });
            }
        }
        return true;
    }

}
