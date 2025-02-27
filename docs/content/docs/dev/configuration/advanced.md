---
title: "Advanced Configuration"
weight: 10
type: docs
---
<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Advanced Configuration Topics

## Anatomy of the Flink distribution

Flink itself consists of a set of classes and dependencies that form the core of Flink's runtime
and must be present when a Flink application is started. The classes and dependencies needed to run
the system handle areas such as coordination, networking, checkpointing, failover, APIs,
operators (such as windowing), resource management, etc.

These core classes and dependencies are packaged in the `flink-dist.jar`, which is available in the `/lib`
folder in the downloaded distribution and is part of the basic Flink container images. 
You can think of these dependencies as similar to Java's core library, which contains classes like `String` and `List`.

In order to keep the core dependencies as small as possible and avoid dependency clashes, the
Flink Core Dependencies do not contain any connectors or libraries (i.e. CEP, SQL, ML) in order to
avoid having an excessive default number of classes and dependencies in the classpath.

The `/lib` directory of the Flink distribution additionally contains various JARs including commonly used modules, 
such as all the required [modules to execute Table jobs](#anatomy-of-table-dependencies) and a set of connector and formats.
These are loaded by default and can be removed from the classpath just by removing them from the `/lib` folder.

Flink also ships additional optional dependencies under the `/opt` folder, 
which can be enabled by moving the JARs in the `/lib` folder.

For more information about classloading, refer to the section on [Classloading in Flink]({{< ref "docs/ops/debugging/debugging_classloading.md" >}}).

## Scala Versions

Different Scala versions are not binary compatible with one another. All Flink dependencies that 
(transitively) depend on Scala are suffixed with the Scala version that they are built for 
(i.e. `flink-table-api-scala-bridge_2.12`).

If you are only using Flink's Java APIs, you can use any Scala version. If you are using Flink's Scala APIs, 
you need to pick the Scala version that matches the application's Scala version.

Please refer to the [build guide]({{< ref "docs/flinkDev/building" >}}#scala-versions) for details 
on how to build Flink for a specific Scala version.

Scala versions after 2.12.8 are not binary compatible with previous 2.12.x versions. This prevents
the Flink project from upgrading its 2.12.x builds beyond 2.12.8. You can build Flink locally for
later Scala versions by following the [build guide]({{< ref "docs/flinkDev/building" >}}#scala-versions).
For this to work, you will need to add `-Djapicmp.skip` to skip binary compatibility checks when building.

See the [Scala 2.12.8 release notes](https://github.com/scala/scala/releases/tag/v2.12.8) for more details.
The relevant section states:

> The second fix is not binary compatible: the 2.12.8 compiler omits certain
> methods that are generated by earlier 2.12 compilers. However, we believe
> that these methods are never used and existing compiled code will continue to
> work.  See the [pull request
> description](https://github.com/scala/scala/pull/7469) for more details.

## Anatomy of Table Dependencies

The Flink distribution contains by default the required JARs to execute Flink SQL Jobs (found in the `/lib` folder), 
in particular:

- `flink-table-api-java-uber-{{< version >}}.jar` &#8594; contains all the Java APIs 
- `flink-table-runtime-{{< version >}}.jar` &#8594; contains the table runtime
- `flink-table-planner-loader-{{< version >}}.jar` &#8594; contains the query planner

{{< hint warning >}}
Previously, these JARs were all packaged into `flink-table.jar`. Since Flink 1.15, this has 
now been split into three JARs in order to allow users to swap the `flink-table-planner-loader-{{< version >}}.jar` 
with `flink-table-planner{{< scala_version >}}-{{< version >}}.jar`.
{{< /hint >}}

While Table Java API artifacts are built into the distribution, Table Scala API artifacts are not 
included by default. When using formats and connectors with the Flink Scala API, you need to either 
download and include these JARs in the distribution `/lib` folder manually (recommended), or package 
them as dependencies in the uber/fat JAR of your Flink SQL Jobs.

For more details, check out how to [connect to external systems]({{< ref "docs/connectors/table/overview" >}}).

### Table Planner and Table Planner Loader

Starting from Flink 1.15, the distribution contains two planners:

- `flink-table-planner{{< scala_version >}}-{{< version >}}.jar`, in `/opt`, contains the query planner
- `flink-table-planner-loader-{{< version >}}.jar`, loaded by default in `/lib`, contains the query planner 
  hidden behind an isolated classpath (you won't be able to address any `io.apache.flink.table.planner` directly)

The two planner JARs contain the same code, but they are packaged differently. In the first case, you must use the 
same Scala version of the JAR. In second case, you do not need to make considerations about Scala, since
it is hidden inside the JAR.

By default,`flink-table-planner-loader` is used by the distribution. If you need to access and use the internals of the query planner, 
you can swap the JARs (copying and pasting `flink-table-planner{{< scala_version >}}.jar` in the distribution `/lib` folder). 
Be aware that you will be constrained to using the Scala version of the Flink distribution that you are using.

{{< hint danger >}}
The two planners cannot co-exist at the same time in the classpath. If you load both of them
in `/lib` your Table Jobs will fail.
{{< /hint >}}

{{< hint warning >}}
In the upcoming Flink versions, we will stop shipping the `flink-table-planner{{< scala_version >}}` artifact in the Flink distribution. 
We strongly suggest migrating your jobs and your custom connectors/formats to work with the API modules, without relying on planner internals. 
If you need some functionality from the planner, which is currently not exposed through the API modules, please open a ticket in order to discuss it with the community.
{{< /hint >}}

## Hadoop Dependencies

**General rule:** It should not be necessary to add Hadoop dependencies directly to your application.
If you want to use Flink with Hadoop, you need to have a Flink setup that includes the Hadoop dependencies, 
rather than adding Hadoop as an application dependency. In other words, Hadoop must be a dependency 
of the Flink system itself and not of the user code that contains the application. Flink will use the
Hadoop dependencies specified by the `HADOOP_CLASSPATH` environment variable, which can be set like this:

```bash
export HADOOP_CLASSPATH=`hadoop classpath`
```

There are two main reasons for this design:

- Some Hadoop interactions happen in Flink's core, possibly before the user application is started. 
  These include setting up HDFS for checkpoints, authenticating via Hadoop's Kerberos tokens, or 
  deploying on YARN.

- Flink's inverted classloading approach hides many transitive dependencies from the core dependencies. 
  This applies not only to Flink's own core dependencies, but also to Hadoop's dependencies when present 
  in the setup. This way, applications can use different versions of the same dependencies without 
  running into dependency conflicts. This is very useful when dependency trees become very large. 

If you need Hadoop dependencies during developing or testing inside the IDE (i.e. for HDFS access),
you should configure these dependencies similar to the scope of the dependencies (i.e. to *test* or 
to *provided*).
