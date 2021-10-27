FROM ubuntu:18.04
LABEL maintainer="foivos.zakkak@manchester.ac.uk"
LABEL name="beehivelab/maxine-dev"
LABEL version="1.2.0"

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    make gcc gdb g++ python python2.7 libnuma-dev \
    git screen rsync ssh wget \
    # Font Libraries
    libfontconfig1 \
    && apt-get clean

# Java 8 u232
RUN wget --no-check-certificate -O /tmp/openjdk-8u222b10.tar.gz https://github.com/AdoptOpenJDK/openjdk8-upstream-binaries/releases/download/jdk8u222-b10/OpenJDK8U-jdk_x64_linux_8u222b10.tar.gz \
    && mkdir /usr/lib/jvm \
    && tar xzvf /tmp/openjdk-8u222b10.tar.gz --directory /usr/lib/jvm/ \
    && mv /usr/lib/jvm/openjdk-8u222-b10 /usr/lib/jvm/java-8-openjdk-amd64

# Install CA certificates
RUN apt-get update && \
    apt-get install -y ca-certificates && \
    update-ca-certificates

# Cross-ISA support
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    lsof gdb-multiarch \
    && apt-get clean

# For AArch64 and ARMv7 support
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    qemu-system-arm ipxe-qemu gcc-aarch64-linux-gnu gcc-arm-none-eabi \
    && apt-get clean

# For RISC-V support
RUN apt-get update && \
    apt-get install -y --no-install-recommends gcc-riscv64-linux-gnu \
    && apt-get clean
# qemu
RUN apt-get update && \
    apt-get install -y --no-install-recommends pkg-config \
    libglib2.0-dev libpixman-1-dev flex bison \
    && apt-get clean
RUN mkdir -p /tmp/riscv \
    && cd /tmp/riscv/ \
    && wget --no-check-certificate https://download.qemu.org/qemu-3.1.0.tar.xz \
    && tar xf qemu-3.1.0.tar.xz \
    && cd /tmp/riscv/qemu-3.1.0 \
    && ./configure --target-list=riscv64-softmmu,riscv32-softmmu,riscv64-linux-user,riscv32-linux-user --prefix=/opt/riscv \
    && make -j$(nproc) && make install && rm -rf /tmp/riscv/qemu-3.1.0
# gdb
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    libncurses5-dev \
    && apt-get clean
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    texinfo \
    && apt-get clean
RUN cd /tmp/riscv/ && \
    wget --no-check-certificate https://ftp.gnu.org/gnu/gdb/gdb-8.2.1.tar.xz \
    && tar xf gdb-8.2.1.tar.xz \
    && cd /tmp/riscv/gdb-8.2.1 \
    && ./configure --target=riscv64-elf --disable-multilib --enable-tui=yes --prefix=/opt/riscv \
    && make -j$(nproc) && make install && cd ~ && rm -rf /tmp/riscv

# For perf support
RUN apt-get update && \
    apt-get install -y --no-install-recommends linux-tools-generic \
    && apt-get clean

ENV MAXINE_SRC=/root/maxine-src
ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
ENV MAXINE_HOME=$MAXINE_SRC/maxine
ENV DEFAULT_VM=maxine
ENV PATH=$PATH:/opt/riscv/bin:$MAXINE_SRC/mx/:$MAXINE_HOME/com.oracle.max.vm.native/generated/linux/

# You will need to download and install SPECJVM2008 manually to the following directory
# ENV SPECJVM2008=$MAXINE_SRC/graal/lib/SPECJVM2008

# enable rsync
RUN sed -i 's/RSYNC_ENABLE=false/RSYNC_ENABLE=true/g' /etc/default/rsync
# Setup rsync
ADD ./rsyncd.conf /etc/rsyncd.conf

RUN mkdir -p ${MAXINE_HOME}

WORKDIR $MAXINE_HOME

CMD service rsync start && bash
