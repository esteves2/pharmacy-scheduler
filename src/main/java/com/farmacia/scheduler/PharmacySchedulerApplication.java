package com.farmacia.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class PharmacySchedulerApplication {

	public static void main(String[] args) {
		System.setProperty("app.db.path", resolveDbPath());
		SpringApplication.run(PharmacySchedulerApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void openBrowser() {
		// Only open automatically when running as a packaged app, not during dev
		if (System.getProperty("app.packaged") == null) return;
		try {
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().browse(URI.create("http://localhost:8080"));
			}
		} catch (IOException e) {
			// Non-fatal — user can open the browser manually
		}
	}

	private static String resolveDbPath() {
		// On Windows: C:\Users\<user>\AppData\Roaming\PharmaciaScheduler\pharmacy.db
		// On Mac/Linux (dev): ~/.PharmaciaScheduler/pharmacy.db
		String appData = System.getenv("APPDATA");
		Path dir = (appData != null)
				? Paths.get(appData, "PharmaciaScheduler")
				: Paths.get(System.getProperty("user.home"), ".PharmaciaScheduler");

		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new RuntimeException("Could not create data directory: " + dir, e);
		}

		return dir.resolve("pharmacy.db").toString();
	}

}
