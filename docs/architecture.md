# SP-EU app ("wisen") architecture

## Summary

In order to support the research phase of the Social Prescribing EU
project we design and implement a software that allows users to
gather, structure and retrieve relevant information about
organizations, events, offers, etc. We use open standards as bedrock
for the information architecture: Semantic Web and RDF. These describe
a way to organize information as one big shared graph of facts. We
build a set of tools and infrastructure on top of this information
graph bedrock: A geo search interface, an information graph display
and editor and a set of input interfaces. We use LLMs to further
support the creation of information graphs.

## System-as-is

We spoke with three potential users of the software-system-to-be
("users"). We assessed their current workflow and derived requirements
for the new system.

Users workflows consist of three activity aspects: information
gathering, continuous information management and assessment, and
spontaneous information retrieval. These three aspects aren't distinct
phases -- they overlap both in time and they overlap in the tools and
information sources used to support these activities.

### Heterogeneous information structure

The kind of information users gather, manage, and retrieve is very
diverse. Often they deal with organizations such as social facilities,
hospitals, governmental organizations etc. Often they deal with people
("Who is responsible for this and that activity?"). Often they deal
with (recurring) events ("Are there any recurring nordic walking
events for elderly nearby?"). Often they deal with process steps ("My
client's husband had a stroke. What are the next steps for
me?"). Often they deal with contact information such as phone numbers,
e-mail addresses or opening hours.

### Information gathering

Users gather information in a variety of ways. They take to ChatGPT,
Google Maps, their own notes, their co-workers' notes, and they talk
to other users directly a lot. Some cities and municipalities offer
catalogues of events and organizations that are relevant to users and
their clients. These catalogues often come in the form of PDFs
distributed via web sites.

Information sources are very heterogeneous in structure.

### Information management and assessment

Again, users manage information is a variety of ways. Currently there
is no shared information system. Users manage information in an ad-hoc
and federated way. Most information gathered is left where it came
from, for example information gathered from Google Maps is not copied
over into a separate system.

One user we spoke to uses a shared spreadsheet to gather information
about organizations relevant to their field and geographical area.

One subtle aspect of information assessment is trutstworthiness. Users
currently take trustworthiness as a function of information source: If
the information originated at a trustworthy colleague, the information
is considered sound. If the information originated on Google Maps, it
is taken with a grain of salt.

### Information retrieval

Even though spontaneous information retrieval is a central aspect of
users' workflow, currently this is not a distinct activity. For
example, users spontaneously look for information on Google Maps to
serve an immediate need. When they found what they were looking for,
they might copy this information over into their own notebook or a
shared spreadsheet or they might not.

## Requirements and Constraints

From an assessment of the system-as-is we derive a set of requirements
and constraints for the system-to-be.

### Sharing

Users want to share information with one another.

### Unified information architecture ("vocabulary")

The system-as-is can hardly be described as a single system. No two
parts involved share a common vocabulary or structure.  This means
that users have to find and enforce uniform structures themselves. For
example, if a user gathers information both from Google Maps and a PDF
catalogue and they want to make this information available to their
colleagues (for example via the above mentioned spreadsheet) and also
provide their own annotations, they have to manually merge all the
information packages. This burden on the users often means that
information is not being unified and not being shared with collegeaus.
The system-to-be therefore requires a unified information
architecture: A shared grammar and vocabulary with which to structure
information.

### Flexibility of information architecture

Since the kind of information that users deal with is very varied, the
vocabulary of the unified information architecture has to be flexible.

### Trustworthiness

Users must still be able to assess the trustworthiness of
information. In the system-as-is this is done by considering the
information source.

One aspect of trustworthiness is timeliness of information. A user
must be able to tell whether a piece of information (such as opening
hours) is outdated or still valid.

### Strong information base ("input")

Since the current system-as-is is basically "everything", it provides
a substantial information base: A lot of organizations have a pin on
Google Maps, a website, or at least a phone number where one can reach
and talk to a human providing further information. In order for the
system-to-be to be useful at all, it has to have a strong information
base as well.

This requirement neccessitates that it is easy to put information into
the system-to-be -- either for users or for primary information
providers such as municipalities etc.

### Efficient retrieval ("output")

A strong information base is only useful if it can be searched
efficiently.

## Proposed solution

The requirements described above are in a field of tension.

* A vocabulary that is restricted in its expressivity makes for more
  efficient input and ouput but it fails to be flexible enough to
  capture the various information kinds.
* A very flexible and expressive vocabulary (for example plain text in
  English) allows to capture a lot of information but it is nearly
  impossible to efficiently retrieve this information without
  undermining trustworthiness.
  
We propose a two-tier architecture to accomodate the requirements
described above:

1. An information bedrock infrastructure based on the open and
   time-tested concepts of Semantic Web and RDF
2. A set of tools on top of this input to facilitate input and output
   including LLM-assisted input and a sophisticated geographical
   search interface.
   
### Bedrock: Semantic Web and RDF

Semantic Web describes a common framework for shared data based on
information graphs. Resource-Description Framework (RDF) is the
technical implementation of this idea.

An information graph consists of a set of facts in the form of simple
subject-predicate-object statements. One such statement might be:
"Begegnungsstätte Hirsch is an organization." Another might be:
"Begegnungsstätte Hirsch has phone number 0707122688." These two
statements form a grpah in that "Begegnungsstätte Hirsch",
"organization", and "0707122688" can be considered nodes, which are
connected by edges labelled "is a" and "has phone number".

Information graphs are very flexible. Nodes and edges can contain
arbitrary labels. Therefore every information that can be represented
as a statement (a triple of subject predicate object) can be expressed
with RDF. Additionally, different information graphs can easily be
merged by simply combining the sets of statements.

In order to enforce structure in an RDF information graph, there are
RDF vocabularies. A vocabulary provides a set of standardized
predicates with defined meanings, and a set of grammar rules (and
sometimes inference rules, which we forego in our system for now). One
such vocabulary is schema.org. For example, in schema.org there is the
predicate "latitude", which describes the latitude of a geo coordinate
(WGS 84). schema.org also defines that the objects of this latitude
predicate must be of type Number or Text and the subject must be of
type "GeoCoordinates" or "Place".

Vocabularies constrain the expressivitiy of RDF, which in turn makes
input and output more straightforward. It is here -- in the design of
a vocabulary -- that we can manage the above mentioned tension of
different requirements. We can build on the insight of RDF experts
around the world by using the schema.org vocabulary as a
foundation. At the same time we build the system-to-be in a
vocabulary-agnostic way so that we could later substitute schema.org
for a more tailored vocabulary.

While technically it is always possible to merge two different
information graphs into one, practically, this operation is really
only valuable if the two graphs (partially) talk about the same
objects. For a merge to be of value to users, these objects have to be
identifiable across graphs. In RDF this is achieved by identifying
objects via globally unique Uniform Resource Identifiers (URIs).

### Input/Output tools

In order to ease input and output of information we build a set of
tools on top of the RDF bedrock.

#### Graph editor

The central tools is a graph viewer/editor. The graph editor is the
human interface for the underlying information graph. The graph editor
allows users to view parts of the information graph and enter new
statements.

#### LLM-assisted input

The graph editor is powerful enough to facilitate any information
input. However, it might sometimes be more efficient for users to
describe a set of statements in natural language. We build an
LLM-assisted input tool that transforms this natural language
information into RDF. The user can then verify if the graph
representation of their information is correct. The user can then
input this information into the system.

##### JSON-LD input

A variation of this tool is a JSON-LD input tool. JSON-LD is a format
for RDF data, which is widely supported by many tools. In particular,
many state-of-the-art LLM tools such as ChatGPT can output
JSON-LD. This opens a workflow that allows users to ask tools such as
ChatGPT to scan a given website for (natural language) information and
transform this information into JSON-LD using schema.org
vocabulary. They can then take this JSON-LD and present it to the
JSON-LD input tool.

#### Sophisticated geo search

We build a geo based search interface for retrieval. The search
interface consists of a semantic search and a set of tools that allow
to narrow down search results in a fine-grained fashion.

#### OpenStreetMap

We build a tool that looks for relevant information on
OpenStreetMap. OpenStreetMap information is presented as
suggestions. If a users wants to use these suggestions, they can
decide to bring these suggestions into the system, where they can then
add their own information.

## Positive Consequences

Semantic Web and RDF provide a time-tested foundation that is flexible
and expressive to facilitate most use cases of the SP-EU project. By
building a set of tools on top of this foundation, we can ease
information input, leading to a strong information base. Information
only enters the system via user input, which helps trustworthiness.

With RDF it is possible to merge multiple information graphs from
different sources. If, for example, a municipality distributes their
information as an RDF graph (as opposed to an unstructured PDF, as is
currently the case), we can use this as ingress into our system.

## Risks

* The architecture described above depends on participants'
  willingness to insert information into the system. If users aren't
  willing to explicitly enter information from their note books,
  Google Maps, their spreadsheets etc. into the system, other users
  will not find any information in the system and will therefore be
  dissatisfied. Alternatively or additionally, information providers
  such as governmental bodies or municipalities may offer their
  information as RDF graphs. If none of them do, the system cannot
  work.
* The notion of an information graph might not be familiar to
  users. There are certain effects such as node sharing or the
  neccessity to use global identifiers that are quite unlike any of
  the document-based systems that users are regularly working
  with. Users therefore have to be trained in using the system.



