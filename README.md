# Resource Allocator API

This project implements a simple resource allocation service. Organizations can register with the service, list the available resources (CPUs and GPUs), set usage limits, and take specific resources offline. Other clients can then request specific resources for their jobs, and the job will be added to the queue. 

## Requirements
- Java 17
    - Install from https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html
- Maven 3.9.5
    - Install from https://maven.apache.org/download.cgi
