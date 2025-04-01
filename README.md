# Yaga

Infrastructure from Code with Pulumi and Scala

## Local development

### Prerequisites:
  - [sbt](https://www.scala-sbt.org/)
  - [just](https://github.com/casey/just)
  - [pulumi](https://www.pulumi.com/docs/iac/download-install/)
  - [besom](https://virtuslab.github.io/besom/docs/getting_started/)
    ```shell
    pulumi plugin install language scala 0.4.0-SNAPSHOT --server github://api.github.com/VirtusLab/besom
    ```

### Publish local by running:
```bash
sbt publishLocal
```

### Running examples:
```bash
cd examples/<EXAMPLE_NAME>

#####
# Set up the infrastructure

sbt "clean; compile"
just infra-up -y

######
# Clean up

just infra-down
```

Building the `aws-lambda-graal` example requires setting the following environment variables:
  * `YAGA_AWS_LAMBDA_GRAAL_REMOTE_USER`
  * `YAGA_AWS_LAMBDA_GRAAL_REMOTE_IP`
describing a remote machine on which the native image is supposed to be built.

The remote builder machine has the following requirements:
  * has to run on Linux
  * needs `native-image` command available on `PATH`
  * is recommended to have a lot of memory and threads available (tested with AWS EC2 c7i.4xlarge image)
