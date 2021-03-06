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
package org.switchyard.tools.ui.tests;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;

import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.m2e.tests.common.AbstractMavenProjectTestCase;
import org.switchyard.tools.ui.M2EUtils;
import org.switchyard.tools.ui.operations.AbstractSwitchYardProjectOperation;
import org.switchyard.tools.ui.operations.CreateBeanServiceOperation;
import org.switchyard.tools.ui.operations.CreateSwitchYardProjectOperation;
import org.switchyard.tools.ui.wizards.NewBeanServiceClassWizardPage;
import org.switchyard.tools.ui.wizards.NewServiceTestClassWizardPage;

/**
 * CreateSwitchYardProjectTest
 * 
 * Tests CreateSwitchYardProjectOperation.
 * 
 * @author Rob Cernich
 */
@SuppressWarnings("restriction")
public class CreateSwitchYardProjectTest extends AbstractMavenProjectTestCase {

    /**
     * TODO: this is probably not the best way to do things. This one test
     * validates most of the functionality in tools.ui. However, starting and
     * stopping a workbench takes a lot of time and we need a project to run the
     * service creation tests so...
     * 
     * Tests the creation of a SwitchYard project: project, pom.xml,
     * switchyard.xml creation.
     * 
     * Test dependency update operation.
     * 
     * Tests the creation of a new SwitchYard service.
     * 
     * @throws Exception if an error occurs or test fails.
     */
    public void testCreateSwitchYardProjectOperation() throws Exception {
        final IWorkspace workspace = ResourcesPlugin.getWorkspace();
        final IProject newProjectHandle = workspace.getRoot().getProject(
                CreateSwitchYardProjectTest.class.getSimpleName());
        String packageName = "test.package_name";
        String groupId = "test.project.group";
        String version = "0.0.1-SNAPSHOT";
        String runtimeVersion = "0.2.0";

        assertTrue("Project already exists.", !newProjectHandle.exists());

        IWorkspaceRunnable op = new CreateSwitchYardProjectOperation(newProjectHandle, null, packageName, groupId,
                version, runtimeVersion, null);
        workspace.run(op, new NullProgressMonitor());

        waitForJobsToComplete();
        newProjectHandle.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
        waitForJobsToComplete();
        assertNoErrors(newProjectHandle);

        // TODO: change true to false once SWITCHYARD-469 is corrected
        assertTrue(
                "Project contains errors",
                newProjectHandle.findMaxProblemSeverity(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE) < IMarker.SEVERITY_ERROR);

        IFile switchyardFile = newProjectHandle.getFile("src/main/resources/META-INF/switchyard.xml");
        assertTrue("Failed to create switchyard.xml", switchyardFile.exists());
        assertTrue("switchyard.xml file is out of sync after project creation",
                switchyardFile.isSynchronized(IFile.DEPTH_ZERO));

        IFile pomFile = newProjectHandle.getFile("pom.xml");
        assertTrue("Failed to create pom.xml", pomFile.exists());
        assertTrue("pom.xml file is out of sync after project creation", pomFile.isSynchronized(IFile.DEPTH_ZERO));

        // Test project update
        op = new AbstractSwitchYardProjectOperation(Collections.singleton(M2EUtils.createSwitchYardDependency(
                "org.switchyard.components", "switchyard-component-bpm")), Collections.<String> emptySet(), true,
                "Testing SwitchYard project update", null) {

            @Override
            protected IProject getProject() {
                return newProjectHandle;
            }

            @Override
            protected void execute(IProgressMonitor monitor) throws CoreException {
                monitor.done();
            }
        };
        workspace.run(op, new NullProgressMonitor());

        waitForJobsToComplete();
        newProjectHandle.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
        waitForJobsToComplete();
        assertNoErrors(newProjectHandle);

        assertTrue("Failed to update pom.xml", pomFile.exists());
        assertTrue("pom.xml file is out of sync after project update", pomFile.isSynchronized(IFile.DEPTH_ZERO));
        assertXMLFilesMatch("pom dependency update failed", pomFile, "test-data/validation/add_dependency_pom.xml");

        // test new bean service and test class
        IFile testServiceFile = newProjectHandle.getFolder("src/main/java").getFolder(packageName.replace('.', '/'))
                .getFile("TestService.java");
        testServiceFile.create(
                CreateSwitchYardProjectTest.class.getClassLoader().getResourceAsStream(
                        "test-data/source/TestService.java"), true, new NullProgressMonitor());
        NewBeanServiceClassWizardPage newBeanPage = new NewBeanServiceClassWizardPage();
        newBeanPage.init(new StructuredSelection(testServiceFile));
        assertTrue("NewBeanServiceClassWizardPage is not initialized correctly.", newBeanPage.isPageComplete());
        NewServiceTestClassWizardPage newTestPage = new NewServiceTestClassWizardPage();
        newTestPage.init(new StructuredSelection(testServiceFile));
        assertTrue("NewServiceTestClassWizardPage is not initialized correctly.", newTestPage.isPageComplete());

        op = new CreateBeanServiceOperation(newBeanPage, newTestPage, null);
        workspace.run(op, new NullProgressMonitor());

        waitForJobsToComplete();
        newProjectHandle.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
        waitForJobsToComplete();
        assertNoErrors(newProjectHandle);

        assertTrue("switchyard.xml missing after bean service creation", switchyardFile.exists());
        assertTrue("switchyard.xml file is out of sync after bean service creation",
                switchyardFile.isSynchronized(IFile.DEPTH_ZERO));
        assertXMLFilesMatch("bean service creation failed", switchyardFile,
                "test-data/validation/create_service_switchyard.xml");

        assertTrue("pom.xml missing after bean service creation", pomFile.exists());
        assertTrue("pom.xml file is out of sync after bean service creation", pomFile.isSynchronized(IFile.DEPTH_ZERO));
        assertXMLFilesMatch("bean service creation failed", pomFile, "test-data/validation/create_service_pom.xml");

        IFile testServiceImplFile = newProjectHandle.getFolder("src/main/java")
                .getFolder(packageName.replace('.', '/')).getFile("TestServiceImpl.java");
        IFile testServiceTestFile = newProjectHandle.getFolder("src/test/java")
                .getFolder(packageName.replace('.', '/')).getFile("TestServiceTest.java");

        assertTrue("TestServiceImpl missing after bean service creation", testServiceImplFile.exists());
        assertTrue("TestServiceImpl file is out of sync after bean service creation",
                testServiceImplFile.isSynchronized(IFile.DEPTH_ZERO));

        assertTrue("testServiceTestFile missing after bean service creation", testServiceTestFile.exists());
        assertTrue("testServiceTestFile file is out of sync after bean service creation",
                testServiceTestFile.isSynchronized(IFile.DEPTH_ZERO));

        // TODO: validate contents of generated files
    }

    private void assertXMLFilesMatch(String label, IFile testFile, String expectedFileLocation) throws Exception {
        Reader testReader = null;
        Reader expectedReader = null;
        try {
            testReader = new InputStreamReader(testFile.getContents());
            expectedReader = new InputStreamReader(CreateSwitchYardProjectTest.class.getClassLoader()
                    .getResourceAsStream(expectedFileLocation));
            Diff diff = XMLUnit.compareXML(testReader, expectedReader);
            assertTrue(label + ": " + diff.toString(), diff.identical());
        } finally {
            if (testReader != null) {
                try {
                    testReader.close();
                } catch (Exception e) {
                    // for codestyle check
                    e.fillInStackTrace();
                }
                testReader = null;
            }
            if (expectedReader != null) {
                try {
                    expectedReader.close();
                } catch (Exception e) {
                    // for codestyle check
                    e.fillInStackTrace();
                }
                expectedReader = null;
            }
        }
    }

}
