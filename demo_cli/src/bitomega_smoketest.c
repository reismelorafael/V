#include "bitomega.h"

#include <stdint.h>
#include <stdio.h>

static int append_transition_csv(FILE *fp,
                                 bitomega_state_t state_prev,
                                 const bitomega_ctx_t *ctx,
                                 bitomega_state_t state_new,
                                 bitomega_dir_t dir) {
  if (!fp || !ctx) {
    return 0;
  }
  if (fprintf(fp,
              "%s,coh=%.2f|ent=%.2f|noi=%.2f|load=%.2f,%s,%s\n",
              bitomega_state_name(state_prev),
              (double)ctx->coherence_in,
              (double)ctx->entropy_in,
              (double)ctx->noise_in,
              (double)ctx->load,
              bitomega_state_name(state_new),
              bitomega_dir_name(dir)) < 0) {
    return 0;
  }
  return 1;
}

int main(void) {
  bitomega_node_t node;
  bitomega_ctx_t sequence[] = {
      {0.78f, 0.20f, 0.15f, 0.30f, 0xA01u},
      {0.92f, 0.12f, 0.10f, 0.25f, 0xA02u},
      {0.88f, 0.24f, 0.74f, 0.95f, 0xA03u},
      {0.54f, 0.82f, 0.88f, 0.40f, 0xA04u},
      {0.86f, 0.25f, 0.08f, 0.35f, 0xA05u},
  };

  node.state = BITOMEGA_ZERO;
  node.dir = BITOMEGA_DIR_NONE;
  node.coherence = 0.50f;
  node.entropy = 0.30f;

  FILE *csv = fopen("bench/results/bitomega_transitions.csv", "w");
  if (!csv) {
    fprintf(stderr, "bitomega_smoketest: failed to create CSV output\n");
    return 2;
  }

  if (fprintf(csv, "state_prev,context,state_new,direction\n") < 0) {
    fclose(csv);
    return 3;
  }

  for (size_t i = 0; i < (sizeof(sequence) / sizeof(sequence[0])); ++i) {
    bitomega_state_t prev = node.state;
    bitomega_status_t status = bitomega_transition(&node, &sequence[i]);
    if (status != BITOMEGA_OK) {
      fprintf(stderr, "bitomega_smoketest: transition failed at step %zu (%d)\n", i, (int)status);
      fclose(csv);
      return 4;
    }
    if (!bitomega_invariant_ok(&node)) {
      fprintf(stderr, "bitomega_smoketest: invariant check failed at step %zu\n", i);
      fclose(csv);
      return 5;
    }
    if (!append_transition_csv(csv, prev, &sequence[i], node.state, node.dir)) {
      fclose(csv);
      return 6;
    }
  }

  fclose(csv);
  printf("bitomega_smoketest: %zu transitions OK\n", sizeof(sequence) / sizeof(sequence[0]));
  return 0;
}
