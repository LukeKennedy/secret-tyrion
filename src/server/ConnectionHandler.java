/*
 * ConnectionHandler.java
 * Oct 7, 2012
 *
 * Simple Web Server (SWS) for CSSE 477
 * 
 * Copyright (C) 2012 Chandan Raj Rupakheti
 * 
 * This program is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License 
 * as published by the Free Software Foundation, either 
 * version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/lgpl.html>.
 * 
 */

package server;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import protocol.HttpRequest;
import protocol.HttpResponse;
import protocol.HttpResponseFactory;
import protocol.Protocol;
import protocol.ProtocolException;

/**
 * This class is responsible for handling a incoming request by creating a
 * {@link HttpRequest} object and sending the appropriate response be creating a
 * {@link HttpResponse} object. It implements {@link Runnable} to be used in
 * multi-threaded environment.
 * 
 * @author Chandan R. Rupakheti (rupakhet@rose-hulman.edu)
 */
public class ConnectionHandler implements Runnable {
	private Server server;
	private Socket socket;

	public ConnectionHandler(Server server, Socket socket) {
		this.server = server;
		this.socket = socket;
	}

	/**
	 * @return the socket
	 */
	public Socket getSocket() {
		return socket;
	}

	/**
	 * The entry point for connection handler. It first parses incoming request
	 * and creates a {@link HttpRequest} object, then it creates an appropriate
	 * {@link HttpResponse} object and sends the response back to the client
	 * (web browser).
	 */
	public void run() {
		Boolean keepOpen = false;
		if (keepOpen) {
		}
		long start = System.currentTimeMillis();

		InputStream inStream = null;
		OutputStream outStream = null;

		try {
			inStream = this.socket.getInputStream();
			outStream = this.socket.getOutputStream();
		} catch (Exception e) {
			// May be have text to log this for further analysis?
			e.printStackTrace();

			server.incrementConnections(1);
			server.decreaseConnectionCount();
			long end = System.currentTimeMillis();
			this.server.incrementServiceTime(end - start);
			return;
		}

		// At this point we have the input and output stream of the socket
		// Now lets create a HttpRequest object
		HttpRequest request = null;
		HttpResponse response = null;
		try {
			request = HttpRequest.read(inStream);
		} catch (ProtocolException pe) {
			// We have some sort of protocol exception. Get its status code and
			// create response
			// We know only two kind of exception is possible inside
			// fromInputStream
			// Protocol.BAD_REQUEST_CODE and Protocol.NOT_IMPLEMENTED_CODE
			int status = pe.getStatus();
			if (status == Protocol.BAD_REQUEST_CODE) {
				response = HttpResponseFactory
						.create400BadRequest(Protocol.CLOSE);
			}
			if (status == Protocol.NOT_IMPLEMENTED_CODE) {
				response = HttpResponseFactory
						.create501NotImplemented(Protocol.CLOSE);
			}
		} catch (Exception e) {
			e.printStackTrace();
			response = HttpResponseFactory.create400BadRequest(Protocol.CLOSE);
		}

		if (response != null) {
			try {
				response.write(outStream);
			} catch (Exception e) {
				e.printStackTrace();
			}
			server.incrementConnections(1);
			server.decreaseConnectionCount();
			long end = System.currentTimeMillis();
			this.server.incrementServiceTime(end - start);
			return;
		}

		try {
			// Fill in the code to create a response for version mismatch.
			// You may want to use constants such as Protocol.VERSION,
			// Protocol.NOT_SUPPORTED_CODE, and more.
			// You can check if the version matches as follows
			if (!request.getVersion().equalsIgnoreCase(Protocol.VERSION)) {
				// Here you checked that the "Protocol.VERSION" string is not
				// equal to the
				// "request.version" string ignoring the case of the letters in
				// both strings

				// TODO: Fill in the rest of the code here
				// Maybe this? - Luke
				response = HttpResponseFactory
						.create505NotSupported(Protocol.CLOSE);
			} else if (request.getMethod().equalsIgnoreCase(Protocol.GET)) {
				String uri = request.getUri();
				String rootDirectory = server.getRootDirectory();
				File file = new File(rootDirectory + uri);
				if (file.exists()) {
					String requestedConnectionState = request.getHeader().get(
							Protocol.CONNECTION.toLowerCase());
					String openOrClosed;
					if (requestedConnectionState
							.equalsIgnoreCase(Protocol.OPEN)) {
						openOrClosed = Protocol.OPEN;
						// System.out.println("Keeping Connection Open");
						keepOpen = true;
					} else {
						System.out.println("Closing Connection");
						openOrClosed = Protocol.CLOSE;
					}

					Date requestDate = null;
					String requestedLastModifiedDate = request.getHeader().get(
							Protocol.IF_LAST_MODIFIED.toLowerCase());
					DateFormat formatter = new SimpleDateFormat(
							"EEE, d MMM yyyy hh:mm:ss z");
					if (requestedLastModifiedDate != null) {
						try {
							requestDate = formatter
									.parse(requestedLastModifiedDate);
						} catch (Exception e) {
							// Nothing!
						}
					}

					if (file.isDirectory()) {
						// Look for default index.html file in a directory
						String location = rootDirectory + uri
								+ System.getProperty("file.separator")
								+ Protocol.DEFAULT_FILE;
						file = new File(location);
						if (file.exists()) {
							// Lets create 200 OK response
							if (requestDate != null) {
								Long lastModified = file.lastModified();
								Date date = new Date(lastModified);
								if (date.after(requestDate)) {
									response = HttpResponseFactory.create200OK(
											file, openOrClosed);
								} else {
									response = HttpResponseFactory
											.create304NotModified(openOrClosed);
								}
							} else {
								response = HttpResponseFactory.create200OK(
										file, openOrClosed);
							}
						} else {
							// File does not exist so lets create 404 file not
							// found code
							response = HttpResponseFactory
									.create404NotFound(Protocol.CLOSE);
						}
					} else {
						// Its a file
						// Lets create 200 OK response
						if (requestDate != null) {
							Long lastModified = file.lastModified();
							Date date = new Date(lastModified);
							if (date.after(requestDate)) {
								response = HttpResponseFactory.create200OK(
										file, openOrClosed);
							} else {
								response = HttpResponseFactory
										.create304NotModified(openOrClosed);
							}
						} else {
							response = HttpResponseFactory.create200OK(file,
									openOrClosed);
						}
					}
				} else {
					// File does not exist so lets create 404 file not found
					// code
					response = HttpResponseFactory
							.create404NotFound(Protocol.CLOSE);
				}
			} else {
				response = HttpResponseFactory
						.create501NotImplemented(Protocol.CLOSE);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			// Write response and we are all done so close the socket
			response.write(outStream);

			// TODO KEEP-ALIVE Stuff should go here!
			// if (!keepOpen) {
			socket.close();
			// }

		} catch (Exception e) {
			e.printStackTrace();
		}

		// Increment number of connections by 1
		server.incrementConnections(1);
		server.decreaseConnectionCount();
		// Get the end time
		long end = System.currentTimeMillis();
		this.server.incrementServiceTime(end - start);
	}
}
