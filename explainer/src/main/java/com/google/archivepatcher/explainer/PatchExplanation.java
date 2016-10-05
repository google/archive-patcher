// Copyright 2016 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.archivepatcher.explainer;

import com.google.archivepatcher.generator.RecommendationReason;
import com.google.archivepatcher.generator.TotalRecompressionLimiter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Aggregate explanation for a collection of {@link EntryExplanation} objects. This class is a
 * convenience for tools that process the output of {@link PatchExplainer}.
 */
public class PatchExplanation {

  /**
   * A {@link Comparator} that performs lexical sorting of the path names within
   * {@link EntryExplanation} objects by treating them as UTF-8 strings.
   */
  private static class EntryExplanationLexicalComparator implements Comparator<EntryExplanation> {
    @Override
    public int compare(EntryExplanation o1, EntryExplanation o2) {
      return path(o1).compareTo(path(o2));
    }
  }

  /**
   * All entries that are new (only present in the new archive).
   */
  private final List<EntryExplanation> explainedAsNew;

  /**
   * All entries that are changed (present in both the old and new archive, but different content).
   */
  private final List<EntryExplanation> explainedAsChanged;

  /**
   * All entries that either unchanged or have a zero-cost in the patch (i.e., a copy or rename).
   */
  private final List<EntryExplanation> explainedAsUnchangedOrFree;

  /**
   * All entries that could have been uncompressed for patching but have been prevented due to
   * resource constraints by a {@link TotalRecompressionLimiter}.
   */
  private final List<EntryExplanation> explainedAsResourceConstrained;

  /**
   * The sum total of the sizes of all the entries that are new in the patch stream.
   */
  private final long estimatedNewSize;

  /**
   * The sum total of the sizes of all the entries that are changed in the patch stream.
   */
  private final long estimatedChangedSize;

  /**
   * The sum total of the sizes of all the entries that were changed but that have been prevented
   * from being uncompressed during patch generation due to resource constraints by a {@link
   * TotalRecompressionLimiter}.
   */
  private final long estimatedResourceConstrainedSize;

  /**
   * Constructs a new aggregate explanation for the specified {@link EntryExplanation}s.
   * @param entryExplanations the explanations for all of the individual entries in the patch
   */
  public PatchExplanation(List<EntryExplanation> entryExplanations) {
    List<EntryExplanation> tempExplainedAsNew = new ArrayList<>();
    List<EntryExplanation> tempExplainedAsChanged = new ArrayList<>();
    List<EntryExplanation> tempExplainedAsUnchangedOrFree = new ArrayList<>();
    List<EntryExplanation> tempExplainedAsResourceConstrained = new ArrayList<>();
    long tempEstimatedNewSize = 0;
    long tempEstimatedChangedSize = 0;
    long tempEstimatedResourceConstrainedSize = 0;
    for (EntryExplanation explanation : entryExplanations) {
      if (explanation.isNew()) {
        tempEstimatedNewSize += explanation.getCompressedSizeInPatch();
        tempExplainedAsNew.add(explanation);
      } else if (explanation.getReasonIncludedIfNotNew()
          == RecommendationReason.RESOURCE_CONSTRAINED) {
        tempEstimatedResourceConstrainedSize += explanation.getCompressedSizeInPatch();
        tempExplainedAsResourceConstrained.add(explanation);
      } else if (explanation.getCompressedSizeInPatch() > 0) {
        tempEstimatedChangedSize += explanation.getCompressedSizeInPatch();
        tempExplainedAsChanged.add(explanation);
      } else {
        tempExplainedAsUnchangedOrFree.add(explanation);
      }
    }
    Comparator<EntryExplanation> comparator = new EntryExplanationLexicalComparator();
    Collections.sort(tempExplainedAsNew, comparator);
    Collections.sort(tempExplainedAsChanged, comparator);
    Collections.sort(tempExplainedAsUnchangedOrFree, comparator);
    Collections.sort(tempExplainedAsResourceConstrained, comparator);
    explainedAsNew = Collections.unmodifiableList(tempExplainedAsNew);
    explainedAsChanged = Collections.unmodifiableList(tempExplainedAsChanged);
    explainedAsUnchangedOrFree = Collections.unmodifiableList(tempExplainedAsUnchangedOrFree);
    explainedAsResourceConstrained =
        Collections.unmodifiableList(tempExplainedAsResourceConstrained);
    estimatedNewSize = tempEstimatedNewSize;
    estimatedChangedSize = tempEstimatedChangedSize;
    estimatedResourceConstrainedSize = tempEstimatedResourceConstrainedSize;
  }

  /**
   * Returns a read-only view of all entries that are new (only present in the new archive), sorted
   * in ascending order lexicographically by path.
   * @return as described
   */
  public List<EntryExplanation> getExplainedAsNew() {
    return explainedAsNew;
  }

  /**
   * Returns a read-only view of all entries that are changed (present in both the old and new
   * archive, but different content), sorted in ascending order lexicographically by path.
   * @return as described
   */
  public List<EntryExplanation> getExplainedAsChanged() {
    return explainedAsChanged;
  }

  /**
   * Returns a read-only view of all entries that either unchanged or have a zero-cost in the patch
   * (i.e., a copy or rename), sorted in ascending order lexicographically by path.
   * @return as described
   */
  public List<EntryExplanation> getExplainedAsUnchangedOrFree() {
    return explainedAsUnchangedOrFree;
  }

  /**
   * Returns a read-only view of all entries that could have been uncompressed for patching but have
   * been prevented due to resource constraints by a {@link TotalRecompressionLimiter}, sorted in
   * ascending order lexicographically by path.
   *
   * @return as described
   */
  public List<EntryExplanation> getExplainedAsResourceConstrained() {
    return explainedAsResourceConstrained;
  }

  /**
   * Returns the sum total of the sizes of all the entries that are new in the patch stream. As
   * noted in {@link EntryExplanation#getCompressedSizeInPatch()}, this is an
   * <strong>approximation</strong>.
   * @return as described
   */
  public long getEstimatedNewSize() {
    return estimatedNewSize;
  }

  /**
   * Returns the sum total of the sizes of all the entries that are changed in the patch stream. As
   * noted in {@link EntryExplanation#getCompressedSizeInPatch()}, this is an
   * <strong>approximation</strong>.
   * @return as described
   */
  public long getEstimatedChangedSize() {
    return estimatedChangedSize;
  }

  /**
   * Returns the sum total of the sizes of all entries that could have been uncompressed for
   * patching but have been prevented due to resource constraints by a {@link
   * TotalRecompressionLimiter}. As noted in {@link EntryExplanation#getCompressedSizeInPatch()},
   * this is an <strong>approximation</strong>.
   *
   * @return as described
   */
  public long getEstimatedResourceConstrainedSize() {
    return estimatedResourceConstrainedSize;
  }

  /**
   * Writes a JSON representation of the data to the specified {@link PrintWriter}. The data has the
   * following form: <code>
   * <br>{
   * <br>&nbsp;&nbsp;estimatedNewSize = &lt;number&gt;,
   * <br>&nbsp;&nbsp;estimatedChangedSize = &lt;number&gt;,
   * <br>&nbsp;&nbsp;explainedAsNew = [
   * <br>&nbsp;&nbsp;&nbsp;&nbsp;&lt;entry_list&gt;
   * <br>&nbsp;&nbsp;],
   * <br>&nbsp;&nbsp;explainedAsChanged = [
   * <br>&nbsp;&nbsp;&nbsp;&nbsp;&lt;entry_list&gt;
   * <br>&nbsp;&nbsp;],
   * <br>&nbsp;&nbsp;explainedAsUnchangedOrFree = [
   * <br>&nbsp;&nbsp;&nbsp;&nbsp;&lt;entry_list&gt;
   * <br>&nbsp;&nbsp;]
   * <br>}
   * </code> <br>
   * Where <code>&lt;entry_list&gt;</code> is a list of zero or more entries of the following form:
   * <code>
   * <br>{ path: '&lt;path_string&gt;', isNew: &lt;true|false&gt;,
   * reasonIncluded: &lt;undefined|'&lt;reason_string'&gt;, compressedSizeInPatch: &lt;number&gt;
   * }
   * </code>
   *
   * @param writer the writer to write the JSON to
   */
  public void writeJson(PrintWriter writer) {
    StringBuilder buffer = new StringBuilder(); // For convenience
    buffer.append("{\n");
    buffer.append("  estimatedNewSize: ").append(getEstimatedNewSize()).append(",\n");
    buffer.append("  estimatedChangedSize: ").append(getEstimatedChangedSize()).append(",\n");
    buffer
        .append("  estimatedResourceConstrainedSize: ")
        .append(getEstimatedResourceConstrainedSize())
        .append(",\n");
    dumpJson(getExplainedAsNew(), "explainedAsNew", buffer, "  ");
    buffer.append(",\n");
    dumpJson(getExplainedAsChanged(), "explainedAsChanged", buffer, "  ");
    buffer.append(",\n");
    dumpJson(getExplainedAsUnchangedOrFree(), "explainedAsUnchangedOrFree", buffer, "  ");
    buffer.append(",\n");
    dumpJson(getExplainedAsResourceConstrained(), "explainedAsResourceConstrained", buffer, "  ");
    buffer.append("\n");
    buffer.append("}");
    writer.write(buffer.toString());
    writer.flush();
  }

  private void dumpJson(
      List<EntryExplanation> explanations, String listName, StringBuilder buffer, String indent) {
    buffer.append(indent).append(listName).append(": [\n");
    Iterator<EntryExplanation> iterator = explanations.iterator();
    while (iterator.hasNext()) {
      EntryExplanation explanation = iterator.next();
      dumpJson(explanation, buffer, indent + "  ");
      if (iterator.hasNext()) {
        buffer.append(",");
      }
      buffer.append("\n");
    }
    buffer.append(indent).append("]");
  }

  private void dumpJson(EntryExplanation entryExplanation, StringBuilder buffer, String indent) {
    String reasonString =
        entryExplanation.isNew()
            ? "undefined"
            : "'" + entryExplanation.getReasonIncludedIfNotNew().toString() + "'";
    buffer
        .append(indent)
        .append("{ ")
        .append("path: '")
        .append(path(entryExplanation))
        .append("', ")
        .append("isNew: ")
        .append(entryExplanation.isNew())
        .append(", ")
        .append("reasonIncluded: ")
        .append(reasonString)
        .append(", ")
        .append("compressedSizeInPatch: ")
        .append(entryExplanation.getCompressedSizeInPatch())
        .append(" }");
  }

  /**
   * Returns the path from an {@link EntryExplanation} as a UTF-8 string.
   * @param explanation the {@link EntryExplanation} to extract the path from
   * @return as described
   */
  private static String path(EntryExplanation explanation) {
    try {
      return new String(explanation.getPath().getData(), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("System doesn't support UTF-8", e);
    }
  }
}
