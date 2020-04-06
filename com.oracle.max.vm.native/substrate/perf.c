/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#include <errno.h>
#include <err.h>

#include <unistd.h>
#include <sys/syscall.h>

#include <sys/ioctl.h>
#include <linux/perf_event.h>

#include "vm.h"

/*
 *	Perf tool wrappers for MaxineVM
 * 	
 *	NOTES:
 *		sudo sh -c 'echo 0 >/proc/sys/kernel/perf_event_paranoid' might be needed for permisions
 *
 */

enum maxine_perf_event_id {
	CACHE_MISSES = 0
};

struct read_format
{
	uint64_t nr;
	struct
	{
		uint64_t value;
		uint64_t id;
	} values[];
};

int *perf_event_fds;
struct perf_event_attr *perf_event_attrs; 
uint64_t *perf_event_ids;
int enabled = 0;

void perfUtilInit(int numOfEvents) {
	if(!enabled) {	
		printf("perfEventInit\n");
		
		// initialize fds
		perf_event_fds = (int*)calloc(numOfEvents, sizeof(int));
		if (!perf_event_fds) {
			errx(1, "error allocating perf_event_fds [%d]: %s", errno, strerror(errno));
	    }

	    // initialize attributes
	    int i;
	    perf_event_attrs = (struct perf_event_attr *)calloc(numOfEvents, sizeof(struct perf_event_attr));
		if (!perf_event_attrs) {
	      	errx(1, "error allocating perf_event_attrs [%d]: %s", errno, strerror(errno));
	    }
	    for(i = 0; i < numOfEvents; i++) {
	      perf_event_attrs[i].size = sizeof(struct perf_event_attr);
	    }

	    // initialize perf_event_ids
	    perf_event_ids = (uint64_t*)calloc(numOfEvents, sizeof(uint64_t));
	    if (!perf_event_ids) {
	    	errx(1, "error allocating perf_event_ids  [%d]: %s", errno, strerror(errno));
	    	exit(6);
	    }
	    enabled = 1;
	} else {
		printf("perf utils already enabled");
	}
}

void perfEventCreate(int id, const char *eventName, int type, int config) {
	printf("id = %d\n", id);
	memset(&perf_event_attrs[id], 0, sizeof(struct perf_event_attr));
	perf_event_attrs[id].type = type;
	perf_event_attrs[id].config = config;
	perf_event_attrs[id].disabled = 1;
	perf_event_attrs[id].inherit = 1;
	perf_event_attrs[id].exclude_kernel = 1;
	perf_event_attrs[id].exclude_hv = 1;
	perf_event_attrs[id].read_format = PERF_FORMAT_GROUP | PERF_FORMAT_ID;
	// -1,	-1	-> 	invalid
	//	1,	-1	->	process 1, all CPUs
	// -1,	 0	->	all processes, current CPU
	//  0,	-1	->	follows current process in all CPUs
	//  0,	 0	->	current process, current CPU
	//  0,	 1	->	current process, CPU 1
	//  1,	 0	->	process 1, current CPU
	//  1,	 1	->	process 1, CPU 1
	if (id == 0) {
		if( (perf_event_fds[id] = syscall(__NR_perf_event_open, &perf_event_attrs[id], 0, -1, -1, 0)) == -1 ) {
	    	errx(1, "error on __NR_perf_event_open at perfEventCreate [%d]: %s\
	    		\nHint: for more persmisions consider altering perf_event_paranoid value. ", errno, strerror(errno));
		}
	} else {
		if( (perf_event_fds[id] = syscall(__NR_perf_event_open, &perf_event_attrs[id], 0, -1, perf_event_fds[0], 0)) == -1 ) {
	    	errx(1, "error on __NR_perf_event_open at perfEventCreate [%d]: %s\
	    		\nHint: for more persmisions consider altering perf_event_paranoid value. ", errno, strerror(errno));
		}
	}
	if (ioctl(perf_event_fds[id], PERF_EVENT_IOC_ID, &perf_event_ids[id]) == -1) {
		errx(1, "error on ioctl at perfEventCreate [%d]: %s", errno, strerror(errno));
	}
}

void perfEventEnable(int id) {
	printf("perfEventEnable\n");
	if (ioctl(perf_event_fds[id], PERF_EVENT_IOC_ENABLE, PERF_IOC_FLAG_GROUP) == -1) {
		errx(1, "error on ioctl at perfEventEnable [%d]: %s", errno, strerror(errno));
	}
}

void perfEventEnableAll() {
	printf("perfEventEnableAll\n");
	if (ioctl(perf_event_fds[0], PERF_EVENT_IOC_ENABLE, PERF_IOC_FLAG_GROUP) == -1) {
		errx(1, "error on ioctl at perfEventEnable [%d]: %s", errno, strerror(errno));
	}
}

void perfEventDisable(int id) {
	printf("perfEventDisable\n");
	if (ioctl(perf_event_fds[id], PERF_EVENT_IOC_DISABLE, PERF_IOC_FLAG_GROUP) == -1) {
		errx(1, "error on ioctl at perfEventDisable [%d]: %s", errno, strerror(errno));
	}
}

void perfEventDisableAll() {
	printf("perfEventDisableAll\n");
	if (ioctl(perf_event_fds[0], PERF_EVENT_IOC_DISABLE, PERF_IOC_FLAG_GROUP) == -1) {
		errx(1, "error on ioctl at perfEventDisable [%d]: %s", errno, strerror(errno));
	}
}

void perfEventReset(int id) {
	printf("perfEventReset\n");
	if (ioctl(perf_event_fds[id], PERF_EVENT_IOC_RESET, PERF_IOC_FLAG_GROUP) == -1) {
		errx(1, "error on ioctl at perfEventReset [%d]: %s", errno, strerror(errno));
	}
}

void perfEventResetAll() {
	printf("perfEventResetAll\n");
	if (ioctl(perf_event_fds[0], PERF_EVENT_IOC_RESET, PERF_IOC_FLAG_GROUP) == -1) {
		errx(1, "error on ioctl at perfEventReset [%d]: %s", errno, strerror(errno));
	}
}

long perfEventRead(int id) {
	char buf[4096];
	struct read_format* rf = (struct read_format*) buf;
	uint64_t eventId, i;

	long retValue = 0;

    // read counters
    int group_leader_fd = perf_event_fds[0];
	if (read(group_leader_fd, buf, sizeof(buf)) == -1) {
    	errx(1, "error on read at perfEventRead [%d]: %s", errno, strerror(errno));
    }

	// loop through the result buffer
	for (i = 0; i < rf -> nr; i++) {
		eventId = rf->values[i].id;

		if ( eventId == perf_event_ids[id]) {
			//printf("found!\n");
			//printf("value = %" PRId64 "\n", rf->values[i].value);
			//printf("id = %" PRId64 "\n", rf->values[i].id);
			retValue = (long) rf->values[i].value;
		}
	}
	return retValue;
}