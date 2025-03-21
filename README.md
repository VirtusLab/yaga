# Yaga

Infrastructure from Code with Pulumi and Scala

## Local development

### Prerequisites:
  - [sbt](https://www.scala-sbt.org/)
  - [just](https://github.com/casey/just)
  - [pulumi](https://www.pulumi.com/docs/iac/download-install/)
  - [besom](https://virtuslab.github.io/besom/docs/getting_started/)
    ```shell
    pulumi plugin install language scala 0.3.2 --server github://api.github.com/VirtusLab/besom
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