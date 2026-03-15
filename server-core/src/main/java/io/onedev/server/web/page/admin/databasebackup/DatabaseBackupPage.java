package io.onedev.server.web.page.admin.databasebackup;

import io.onedev.commons.utils.FileUtils;
import io.onedev.commons.utils.ZipUtils;
import io.onedev.server.OneDev;
import io.onedev.server.cluster.ClusterService;
import io.onedev.server.data.InstanceArchiveManager;
import io.onedev.server.data.InstanceArchiveRestoreExecutor;
import io.onedev.server.data.RepositoryArchiveMode;
import io.onedev.server.service.SettingService;
import io.onedev.server.data.DataService;
import io.onedev.server.web.component.fileupload.FileUploadField;
import io.onedev.server.web.editable.BeanContext;
import io.onedev.server.web.page.admin.AdministrationPage;
import org.apache.tika.mime.MimeTypes;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.feedback.FencedFeedbackPanel;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.link.ResourceLink;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.AbstractResource;
import org.apache.wicket.util.lang.Bytes;

import static io.onedev.server.web.translation.Translation._T;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class DatabaseBackupPage extends AdministrationPage {

	public DatabaseBackupPage(PageParameters params) {
		super(params);
	}

	private ClusterService getClusterService() {
		return OneDev.getInstance(ClusterService.class);
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();
		add(new FencedFeedbackPanel("feedback", this));
		
		add(new Label("leadServer", new LoadableDetachableModel<String>() {
			@Override
			protected String load() {
				return getClusterService().getLeaderServerAddress();
			}
		}) {
			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(getClusterService().getServerAddresses().size() > 1);
			}
		});
		BackupSettingHolder backupSettingHolder = new BackupSettingHolder();
		backupSettingHolder.setBackupSetting(OneDev.getInstance(SettingService.class).getBackupSetting());
		Form<?> form = new Form<Void>("backupSetting") {

			@Override
			protected void onSubmit() {
				super.onSubmit();
				OneDev.getInstance(SettingService.class).saveBackupSetting(backupSettingHolder.getBackupSetting());
				getSession().success(_T("Backup settings updated"));
				
				setResponsePage(DatabaseBackupPage.class);
			}
			
		};
		form.add(BeanContext.edit("editor", backupSettingHolder));
		form.add(new ResourceLink<Void>("backupNow", new AbstractResource() {

			@Override
			protected ResourceResponse newResourceResponse(Attributes attributes) {
				ResourceResponse response = new ResourceResponse();
				response.setContentType(MimeTypes.OCTET_STREAM);
				response.disableCaching();
				response.setFileName("backup.zip");
				response.setWriteCallback(new WriteCallback() {

					@Override
					public void writeData(Attributes attributes) throws IOException {
						File tempDir = FileUtils.createTempDir("backup");
						try {
							DataService databaseManager = OneDev.getInstance(DataService.class);
							databaseManager.exportData(tempDir);
							ZipUtils.zip(tempDir, attributes.getResponse().getOutputStream());
						} finally {
							FileUtils.deleteDir(tempDir);
						}
					}				
				});

				return response;

			}
			
		}));
		form.add(new ResourceLink<Void>("backupArchiveShallowNow", newArchiveResource(RepositoryArchiveMode.SHALLOW,
				"instance-backup-shallow.zip")));
		form.add(new ResourceLink<Void>("backupArchiveNow",
				newArchiveResource(RepositoryArchiveMode.FULL, "instance-backup.zip")));

		var restoreForm = new Form<Void>("restoreArchive");
		restoreForm.setMultiPart(true);
		restoreForm.setMaxSize(Bytes.gigabytes(10));
		var archiveField = new FileUploadField("archive");
		archiveField.setRequired(true);
		restoreForm.add(archiveField);
		restoreForm.add(new Button("restore") {

			@Override
			public void onSubmit() {
				super.onSubmit();
				var uploads = archiveField.getFileUploads();
				if (uploads == null || uploads.isEmpty()) {
					error(_T("Please choose an archive file to restore"));
					return;
				}

				File uploadedArchive = FileUtils.createTempFile("uploaded-instance-archive", ".zip");
				try {
					uploads.get(0).writeTo(uploadedArchive);
					var logFile = OneDev.getInstance(InstanceArchiveRestoreExecutor.class).launch(uploadedArchive);
					getSession().success(_T("Instance restore has been started. The server will enter maintenance mode and may become unavailable temporarily. Follow progress in " + logFile.getAbsolutePath()));
					setResponsePage(DatabaseBackupPage.class);
				} catch (Exception e) {
					FileUtils.deleteFile(uploadedArchive);
					throw new RuntimeException(e);
				}
			}
		});
		restoreForm.add(AttributeModifier.append("class", "leave-confirm"));
		restoreForm.get("restore").add(AttributeModifier.replace("onclick",
				"return confirm('This will replace the current database, repositories, attachments, and instance assets. Continue?');"));
		add(restoreForm);

		add(form);
	}

	private AbstractResource newArchiveResource(RepositoryArchiveMode repositoryMode, String fileName) {
		return new AbstractResource() {

			@Override
			protected ResourceResponse newResourceResponse(Attributes attributes) {
				ResourceResponse response = new ResourceResponse();
				response.setContentType(MimeTypes.OCTET_STREAM);
				response.disableCaching();
				response.setFileName(fileName);
				response.setWriteCallback(new WriteCallback() {

					@Override
					public void writeData(Attributes attributes) throws IOException {
						File tempFile = FileUtils.createTempFile("instance-backup", ".zip");
						try {
							OneDev.getInstance(InstanceArchiveManager.class).exportArchive(tempFile, repositoryMode);
							Files.copy(tempFile.toPath(), attributes.getResponse().getOutputStream());
						} finally {
							FileUtils.deleteFile(tempFile);
						}
					}
				});
				return response;
			}
		};
	}

	@Override
	protected Component newTopbarTitle(String componentId) {
		return new Label(componentId, _T("Backup & Restore"));
	}

}
