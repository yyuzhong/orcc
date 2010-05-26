/*
 * Copyright (c) 2009, IETR/INSA of Rennes
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *   * Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   * Neither the name of the IETR/INSA of Rennes nor the names of its
 *     contributors may be used to endorse or promote products derived from this
 *     software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
package net.sf.orcc.interpreter;

import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;

import net.sf.orcc.debug.model.OrccProcess;
import net.sf.orcc.ir.Actor;

/**
 * This class describes a display actor.
 * 
 * @author Pierre-Laurent Lagalaye
 * 
 */
public class DisplayActor extends AbstractInterpretedActor {

	private static DisplayActor instance;

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private long lastTime;

	private static int clip(int n) {
		if (n < 0) {
			return 0;
		} else if (n > 255) {
			return 255;
		} else {
			return n;
		}
	}

	private static int convertYCbCrtoRGB(int y, int cb, int cr) {
		int C = y - 16;
		int D = cb - 128;
		int E = cr - 128;

		int r = clip((298 * C + 409 * E + 128) >> 8);
		int g = clip((298 * C - 100 * D - 208 * E + 128) >> 8);
		int b = clip((298 * C + 516 * D + 128) >> 8);

		return (r << 16) | (g << 8) | b;
	}

	private BufferStrategy buffer;

	private Canvas canvas;

	private CommunicationFifo fifo_B;
	private CommunicationFifo fifo_HEIGHT;
	private CommunicationFifo fifo_WIDTH;

	private JFrame frame;

	public int height;

	private BufferedImage image;

	private int numImages;

	private OrccProcess process;

	private boolean userInterruption;

	public int width;

	public int x;

	public int y;

	public DisplayActor(String id, Actor actor, OrccProcess process) {
		super(id, actor);

		this.process = process;
		frame = new JFrame("display");

		canvas = new Canvas();
		frame.add(canvas);
		frame.setVisible(true);

		frame.addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosing(WindowEvent e) {
				// out.println("Display closed after " + numImages
				// + " images");
				// Indicate the end of interpretation (will be returned to main
				userInterruption = true;
			}

		});

		instance = this;
		userInterruption = false;
	}

	@Override
	public void close() {
		if (instance != null) {
			instance.frame.dispose();
		}
	}

	@Override
	public void initialize() {
		// Connect to FIFOs
		fifo_WIDTH = ioFifos.get("WIDTH");
		fifo_HEIGHT = ioFifos.get("HEIGHT");
		fifo_B = ioFifos.get("B");
		lastTime = System.currentTimeMillis();
	}

	@Override
	public Integer run() {
		int running = 0;

		if (userInterruption) {
			return -1;
		}

		if ((fifo_WIDTH.hasTokens(1)) && (fifo_HEIGHT.hasTokens(1))) {
			setVideoSize();
			running = 1;
		}

		while (fifo_B.hasTokens(384)) {
			writeMB();
			running = 1;
			if (buffer != null) {
				Graphics graphics = buffer.getDrawGraphics();
				graphics.drawImage(image, 0, 0, null);
				buffer.show();
				graphics.dispose();
			}
		}

		return running;
	}

	@Override
	public Integer schedule() {
		int running = 0;

		if (userInterruption) {
			return -2;
		}

		if ((fifo_WIDTH.hasTokens(1)) && (fifo_HEIGHT.hasTokens(1))) {
			setVideoSize();
			running = 1;
		}

		if (fifo_B.hasTokens(384)) {
			writeMB();
			running = 1;
			if (buffer != null) {
				Graphics graphics = buffer.getDrawGraphics();
				graphics.drawImage(image, 0, 0, null);
				buffer.show();
				graphics.dispose();
			}
		}

		return running;
	}

	private void setVideoSize() {
		Object[] fifoWidth = new Integer[1];
		Object[] fifoHeight = new Integer[1];

		fifo_WIDTH.get(fifoWidth);
		fifo_HEIGHT.get(fifoHeight);

		int newWidth = ((Integer) fifoWidth[0]) << 4;
		int newHeight = ((Integer) fifoHeight[0]) << 4;

		if (newWidth != this.width || newHeight != this.height) {

			process.write("New video stream display size : " + newWidth + "x"
					+ newHeight + "\n");
			this.width = newWidth;
			this.height = newHeight;

			canvas.setSize(this.width, this.height);
			frame.pack();

			canvas.createBufferStrategy(2);
			buffer = canvas.getBufferStrategy();

			image = new BufferedImage(this.width, this.height,
					BufferedImage.TYPE_INT_RGB);
		}
	}

	@Override
	public int step(boolean doStepInto) {
		return schedule();
	}

	private void writeMB() {
		Object[] mb = new Integer[384];
		fifo_B.get(mb);

		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 8; j++) {

				int u = (Integer) mb[256 + 8 * i + j];
				int v = (Integer) mb[320 + 8 * i + j];
				int y0 = (Integer) mb[2 * i * 16 + 2 * j];
				int y1 = (Integer) mb[2 * i * 16 + 2 * j + 1];
				int y2 = (Integer) mb[(2 * i + 1) * 16 + 2 * j];
				int y3 = (Integer) mb[(2 * i + 1) * 16 + 2 * j + 1];

				int rgb0 = convertYCbCrtoRGB(y0, u, v);
				int rgb1 = convertYCbCrtoRGB(y1, u, v);
				int rgb2 = convertYCbCrtoRGB(y2, u, v);
				int rgb3 = convertYCbCrtoRGB(y3, u, v);

				image.setRGB(x + j * 2, y + i * 2, rgb0);
				image.setRGB(x + j * 2 + 1, y + i * 2, rgb1);
				image.setRGB(x + j * 2, y + i * 2 + 1, rgb2);
				image.setRGB(x + j * 2 + 1, y + i * 2 + 1, rgb3);
			}
		}

		x += 16;
		if (x == width) {
			x = 0;
			y += 16;
		}

		if (y == height) {
			process.write("Displaying picture " + numImages + " complete\n");
			x = 0;
			y = 0;
			numImages++;
			long timeFrame = System.currentTimeMillis() - lastTime;
			lastTime = System.currentTimeMillis();
			process.write("Frame decoding time = " + timeFrame + "\n");
		}
	}

}
