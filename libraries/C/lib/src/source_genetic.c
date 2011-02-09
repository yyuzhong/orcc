/*
 * Copyright (c) 2009, IRISA of Rennes
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

#include "orcc.h"
#include "orcc_fifo.h"
#include "orcc_util.h"

// from APR
/* Ignore Microsoft's interpretation of secure development
 * and the POSIX string handling API
 */
#if defined(_MSC_VER) && _MSC_VER >= 1400
#ifndef _CRT_SECURE_NO_DEPRECATE
#define _CRT_SECURE_NO_DEPRECATE
#endif
#pragma warning(disable: 4996)
#endif

static FILE *F = NULL;

// Called before any *_scheduler function.
void source_genetic_initialize() {
	if (input_file == NULL) {
		print_usage();
		fprintf(stderr, "No input file given!\n");
		wait_for_key();
		exit(1);
	}

	F = fopen(input_file, "rb");
	if (F == NULL) {
		if (input_file == NULL) {
			input_file = "<null>";
		}

		fprintf(stderr, "could not open file \"%s\"\n", input_file);
		wait_for_key();
		exit(1);
	}
}

extern struct fifo_i8_s *source_genetic_O;

int stop = 0;
int restart = 0;

void source_genetic_scheduler(struct schedinfo_s *si) {
	unsigned char *ptr;
	int i = 0;
	int n;


	if (feof(F)) {
		fseek(F, 0, 0);
	}

	while (fifo_i8_has_room(source_genetic_O, 1) && !stop) {
		i8 source_genetic_O_buf[1];
		ptr = fifo_i8_write(source_genetic_O, source_genetic_O_buf, 1);
		n = fread(ptr, 1, 1, F);
		if (n < 1) {
			if (feof(F)) {
				stop = 1;
			}
		}
		else{
			i++;
			fifo_i8_write_end(source_genetic_O, source_genetic_O_buf, 1);
		}
	}

	si->num_firings = i;
	si->reason = full;
	si->ports = 0x01; // FIFO connected to first output port is empty
}
