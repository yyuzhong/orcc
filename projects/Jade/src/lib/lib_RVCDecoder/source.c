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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "source.h"

// Start code AVC
static unsigned char AVCStartCode[4] = {0x00,0x00,0x00, 0x01};
static int AVCFile;

static int data_length;
static unsigned char* data;
static int nb;

extern int* stopVar;



// Called before any *_scheduler function.
void source_init() {
	AVCFile = 0;
}

int source_sizeOfFile() { 
	if(!data_length){
		return 0;
	}else if(AVCFile){
		return data_length + 4; 
	}
	else{
		return data_length;
	}
}


void source_rewind() {
}

void source_readNBytes(unsigned char *outTable, unsigned short nbTokenToRead){
	if(AVCFile && !nb){
		memcpy(outTable, AVCStartCode, 4);
		memcpy(outTable + 4, data, nbTokenToRead-4);
		data_length = data_length - nbTokenToRead + 4;
	}else{
		memcpy(outTable, data + nb*4096, nbTokenToRead);
		data_length = data_length - nbTokenToRead;
	}

	nb++;

	if (data_length == 0){
		*stopVar = 1;
	}
}


void source_sendNal(unsigned char* nal, int nal_length){
	nb = 0;
	data = nal;
	data_length = nal_length;
}

void source_isAVCFile(){
	AVCFile = 1;
}