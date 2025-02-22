/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.developerstudio.eclipse.esb.project.ui.wizard;

import java.io.File;
import java.net.URL;
import java.util.Map;

import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.wso2.developerstudio.eclipse.artifact.connector.model.ConnectorModel;
import org.wso2.developerstudio.eclipse.artifact.connector.ui.wizard.ConnectorCreationWizard;
import org.wso2.developerstudio.eclipse.distribution.project.model.DistributionProjectModel;
import org.wso2.developerstudio.eclipse.distribution.project.ui.wizard.DistributionProjectWizard;
import org.wso2.developerstudio.eclipse.docker.distribution.model.DockerModel;
import org.wso2.developerstudio.eclipse.docker.distribution.ui.wizard.ContainerProjectCreationWizard;
import org.wso2.developerstudio.eclipse.esb.project.Activator;
import org.wso2.developerstudio.eclipse.esb.project.model.ESBSolutionProjectModel;
import org.wso2.developerstudio.eclipse.esb.project.utils.ESBImageUtils;
import org.wso2.developerstudio.eclipse.general.project.model.GeneralProjectModel;
import org.wso2.developerstudio.eclipse.general.project.ui.wizard.GeneralProjectWizard;
import org.wso2.developerstudio.eclipse.logging.core.IDeveloperStudioLog;
import org.wso2.developerstudio.eclipse.logging.core.Logger;
import org.wso2.developerstudio.eclipse.maven.util.MavenUtils;
import org.wso2.developerstudio.eclipse.platform.core.exception.ObserverFailedException;
import org.wso2.developerstudio.eclipse.platform.core.project.model.ProjectWizardSettings;
import org.wso2.developerstudio.eclipse.platform.ui.wizard.AbstractWSO2ProjectCreationWizard;
import org.wso2.developerstudio.eclipse.platform.ui.wizard.pages.DockerKubernetesDetailsPage;
import org.wso2.developerstudio.eclipse.platform.ui.wizard.pages.MavenDetailsPage;
import org.wso2.developerstudio.eclipse.platform.ui.wizard.pages.ProjectOptionsDataPage;
import org.wso2.developerstudio.eclipse.platform.ui.wizard.pages.ProjectOptionsPage;


/**
 * This wizard class is to create ESB, Registry, Connector Exporter, Composite
 * projects as an ESB solution project
 * 
 */
public class ESBSolutionProjectCreationWizard extends AbstractWSO2ProjectCreationWizard {
	private static final String ESB_GRAPHICAL_PERSPECTIVE = "org.wso2.developerstudio.eclipse.gmf.esb.diagram.custom.perspective";

	private static IDeveloperStudioLog log = Logger.getLog(Activator.PLUGIN_ID);
	private IProject project;
	private ESBSolutionProjectModel esbSolutionProjectModel;
	private File pomFile;
	private String CAPP_ARTIFACT_ID = "CompositeApplication";
	private String CONNECTOR_ARTIFACT_ID = "ConnectorExporter";
	private String DOCKER_ARTIFACT_ID = "DockerKubernetsExporter";
	private String REGISTRY_ARTIFACT_ID = "Registry";
	private String PROJECT_WIZARD_TITLE = "New ESB Solution Project";
	private DockerKubernetesDetailsPage containerDetailPage;

	public ESBSolutionProjectCreationWizard() {
		setEsbSolutionProjectModel(new ESBSolutionProjectModel());
		setModel(esbSolutionProjectModel);
		setDefaultPageImageDescriptor(ESBImageUtils.getInstance().getImageDescriptor("esb-project-wizard.png"));
		setWindowTitle(PROJECT_WIZARD_TITLE);
	}
	
	@Override
	public IWizardPage getNextPage(IWizardPage currentPage) {
		if (currentPage == getMainWizardPage() && getModel().isContainerExporterProjectChecked()) {
			return containerDetailPage;
		} else {
			return getMavenDetailPage();
		}
	}

	@Override
	public void addPages() {
		super.addPages();
		containerDetailPage = new DockerKubernetesDetailsPage(getModel());
		addPage(containerDetailPage);
	}

	@Override
	public boolean canFinish() {
		if (getContainer().getCurrentPage() == getMainWizardPage() && getModel().isContainerExporterProjectChecked()) {
			return false;
		}

		return true;
	}

	public boolean performFinish() {
		File location = esbSolutionProjectModel.getLocation();

		// Creating ESB project
		ESBProjectWizard esbProjectWizard = new ESBProjectWizard();
		esbProjectWizard.setEsbProjectModel(esbSolutionProjectModel);
		esbProjectWizard.setModel(esbSolutionProjectModel);
		esbProjectWizard.performFinish();
		pomFile = esbProjectWizard.getPomFile();
		// Creating Registry Project
		if (esbSolutionProjectModel.isRegistryProjectChecked()) {
			GeneralProjectWizard generalProjectWizard = new GeneralProjectWizard();
			GeneralProjectModel generalProjectModel = new GeneralProjectModel();
			String registryProjectName = esbSolutionProjectModel.getRegistryProjectName();
			try {
				generalProjectModel.setProjectName(registryProjectName);
				generalProjectModel.setLocation(location);
				updateMavenInformation(pomFile,REGISTRY_ARTIFACT_ID);
				generalProjectModel.setGroupId(esbSolutionProjectModel.getGroupId());
				generalProjectModel.setMavenInfo(esbSolutionProjectModel.getMavenInfo());
				generalProjectModel.setIsUserDefine(esbSolutionProjectModel.isUserSet());
				generalProjectModel.setSelectedWorkingSets(esbSolutionProjectModel.getSelectedWorkingSets());
			} catch (ObserverFailedException e1) {
				log.error("Failed to set project name : " + registryProjectName, e1);
			}
			generalProjectWizard.setModel(generalProjectModel);
			generalProjectWizard.performFinish();
		}

		// Creating connector Exporter Project
		if (esbSolutionProjectModel.isConnectorExporterProjectChecked()) {
			ConnectorCreationWizard connectorWizard = new ConnectorCreationWizard();
			ConnectorModel connectorModel = new ConnectorModel();
			String connectorProjectName = esbSolutionProjectModel.getConnectorExporterProjectName();
			try {
				connectorModel.setProjectName(connectorProjectName);
				connectorModel.setLocation(location);
				updateMavenInformation(pomFile,CONNECTOR_ARTIFACT_ID);
				connectorModel.setGroupId(esbSolutionProjectModel.getGroupId());
				connectorModel.setMavenInfo(esbSolutionProjectModel.getMavenInfo());
				connectorModel.setIsUserDefine(esbSolutionProjectModel.isUserSet());
				connectorModel.setSelectedWorkingSets(esbSolutionProjectModel.getSelectedWorkingSets());
			} catch (ObserverFailedException e1) {
				log.error("Failed to set project name : " + connectorProjectName, e1);
			}
			connectorWizard.setModel(connectorModel);
			connectorWizard.performFinish();
		}
		
        // Creating docker image exporter Project
		if (esbSolutionProjectModel.isContainerExporterProjectChecked()
				&& esbSolutionProjectModel.isDockerContainerSelected()) {
			ContainerProjectCreationWizard dockerWizard = new ContainerProjectCreationWizard();
            DockerModel dockerModel = new DockerModel();
            String dockerProjectName = esbSolutionProjectModel.getDockerExporterProjectName();
            try {
                dockerModel.setProjectName(dockerProjectName);
                dockerModel.setLocation(location);
                updateMavenInformation(pomFile, DOCKER_ARTIFACT_ID);
                dockerModel.setGroupId(esbSolutionProjectModel.getGroupId());
                dockerModel.setMavenInfo(esbSolutionProjectModel.getMavenInfo());
                dockerModel.setIsUserDefine(esbSolutionProjectModel.isUserSet());
                dockerModel.setSelectedWorkingSets(esbSolutionProjectModel.getSelectedWorkingSets());
                dockerModel.setDockerRemoteRepository(esbSolutionProjectModel.getDockerRemoteRepository());
                dockerModel.setDockerRemoteTag(esbSolutionProjectModel.getDockerRemoteTag());
                dockerModel.setDockerTargetRepository(esbSolutionProjectModel.getDockerTargetRepository());
                dockerModel.setDockerTargetTag(esbSolutionProjectModel.getDockerTargetTag());
                dockerModel.setContainerExporterProjectChecked(
                        esbSolutionProjectModel.isContainerExporterProjectChecked());
                dockerModel.setDockerContainerSelected(esbSolutionProjectModel.isDockerContainerSelected());
            } catch (ObserverFailedException e1) {
                log.error("Failed to set project name : " + dockerProjectName, e1);
            }
            dockerWizard.setModel(dockerModel);
            dockerWizard.performFinish();
        }

		// Creating Kubernetes image exporter Project
		if (esbSolutionProjectModel.isContainerExporterProjectChecked()
				&& !esbSolutionProjectModel.isDockerContainerSelected()) {
			ContainerProjectCreationWizard containerWizard = new ContainerProjectCreationWizard();
			DockerModel kubeModel = new DockerModel();
			String kubeProjectName = esbSolutionProjectModel.getDockerExporterProjectName();
			try {
				kubeModel.setProjectName(kubeProjectName);
				kubeModel.setLocation(location);
				updateMavenInformation(pomFile, DOCKER_ARTIFACT_ID);
				kubeModel.setGroupId(esbSolutionProjectModel.getGroupId());
				kubeModel.setMavenInfo(esbSolutionProjectModel.getMavenInfo());
				kubeModel.setIsUserDefine(esbSolutionProjectModel.isUserSet());
				kubeModel.setSelectedWorkingSets(esbSolutionProjectModel.getSelectedWorkingSets());
				kubeModel.setKubeContainerName(esbSolutionProjectModel.getKubeContainerName());
				kubeModel.setKubeReplicsas(esbSolutionProjectModel.getKubeReplicsas());
				kubeModel.setKubeReplicsas(esbSolutionProjectModel.getKubeReplicsas());
				kubeModel.setKubeRemoteRepository(esbSolutionProjectModel.getKubeRemoteRepository());
				kubeModel.setKubeRemoteTag(esbSolutionProjectModel.getKubeRemoteTag());
				kubeModel.setKubeTargetRepository(esbSolutionProjectModel.getKubeTargetRepository());
				kubeModel.setKubeTargetTag(esbSolutionProjectModel.getKubeTargetTag());
				kubeModel.setKubeExposePort(esbSolutionProjectModel.getKubeExposePort());
				kubeModel.setContainerExporterProjectChecked(
						esbSolutionProjectModel.isContainerExporterProjectChecked());
				kubeModel.setDockerContainerSelected(esbSolutionProjectModel.isDockerContainerSelected());

				for (Map.Entry<String, String> item : esbSolutionProjectModel.getCustomParameters().entrySet()) {
					kubeModel.getCustomParameters().put(item.getKey(), item.getValue());
				}
			} catch (ObserverFailedException e1) {
				log.error("Failed to set project name : " + kubeProjectName, e1);
			}
			containerWizard.setModel(kubeModel);
			containerWizard.performFinish();
		}

		// Creating Composite Project
		if (esbSolutionProjectModel.isCappProjectChecked()) {
			DistributionProjectWizard distributionProjectWizard = new DistributionProjectWizard();
			DistributionProjectModel distributionModel = new DistributionProjectModel();
			String distributionProjectName = esbSolutionProjectModel.getCompositeApplicationProjectName();
			try {
				esbSolutionProjectModel.setProjectName(distributionProjectName);
				esbSolutionProjectModel.setLocation(location);
				distributionModel.setProjectName(distributionProjectName);
				distributionModel.setLocation(esbSolutionProjectModel.getLocation());
				distributionModel.setIsUserDefine(esbSolutionProjectModel.isUserSet());
				distributionModel.setLocation(esbSolutionProjectModel.getLocation());
				updateMavenInformation(pomFile,CAPP_ARTIFACT_ID);
				distributionModel.setGroupId(esbSolutionProjectModel.getGroupId());
				distributionModel.setMavenInfo(esbSolutionProjectModel.getMavenInfo());
				distributionModel.setSelectedOption(esbSolutionProjectModel.getSelectedOption());
				distributionModel.setSelectedWorkingSets(esbSolutionProjectModel.getSelectedWorkingSets());
			} catch (ObserverFailedException e) {
				log.error("Failed to set properties for project : " + distributionProjectName, e);
			}
			distributionProjectWizard.setModel(distributionModel);
			distributionProjectWizard.performFinish();
		}
		
        getShell().getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                if (!ESB_GRAPHICAL_PERSPECTIVE.equals(window.getActivePage().getPerspective().getId())) {
                    try {
                        PlatformUI.getWorkbench().showPerspective(ESB_GRAPHICAL_PERSPECTIVE, window);
                    } catch (Exception e) {
                        log.error("Cannot switch to ESB Graphical Perspective", e);
                    }
                }
            }
        });

        return true;
	}

	private void updateMavenInformation(File pomLocation, String name) {
		MavenProject mavenProject = getMavenProject(pomLocation);
		esbSolutionProjectModel.getMavenInfo().setGroupId(mavenProject.getGroupId());
		esbSolutionProjectModel.getMavenInfo().setArtifactId(mavenProject.getArtifactId()+name);
		esbSolutionProjectModel.getMavenInfo().setVersion(mavenProject.getVersion());
	}

	public MavenProject getMavenProject(File pomLocation) {
		MavenProject mavenProject = null;
		if (pomLocation != null && pomLocation.exists()) {
			try {
				mavenProject = MavenUtils.getMavenProject(pomLocation);
			} catch (Exception e) {
				log.error("error reading pom file", e);
			}
		}
		return mavenProject;

	}

	public ESBSolutionProjectModel getEsbSolutionProjectModel() {
		return esbSolutionProjectModel;
	}

	public void setEsbSolutionProjectModel(ESBSolutionProjectModel esbProjectModel) {
		this.esbSolutionProjectModel = esbProjectModel;
	}

	public IResource getCreatedResource() {
		return project;
	}

	public void setCurrentSelection(ISelection currentSelection) {
		// TODO Auto-generated method stub
		super.setCurrentSelection(currentSelection);
	}

}