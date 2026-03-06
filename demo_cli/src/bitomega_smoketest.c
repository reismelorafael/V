#include "bitomega.h"

#include <stdint.h>
#include <stdio.h>

typedef struct {
  const char *name;
  bitomega_state_t start_state;
  bitomega_dir_t start_dir;
  float start_coh;
  float start_ent;
  bitomega_ctx_t ctx;
  bitomega_state_t expected_state;
  bitomega_dir_t expected_dir;
} bitomega_case_t;

static int append_transition_csv(FILE *fp,
                                 const char *scenario,
                                 bitomega_state_t state_prev,
                                 const bitomega_ctx_t *ctx,
                                 bitomega_state_t state_new,
                                 bitomega_dir_t dir) {
  if (!fp || !ctx || !scenario) {
    return 0;
  }
  if (fprintf(fp,
              "%s,%s,coh=%.2f|ent=%.2f|noi=%.2f|load=%.2f,%s,%s\n",
              scenario,
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
  const bitomega_case_t cases[] = {
      {
          "FLOW_TO_LOCK",
          BITOMEGA_FLOW,
          BITOMEGA_DIR_FORWARD,
          0.92f,
          0.10f,
          {0.95f, 0.10f, 0.10f, 0.25f, 0xB001u},
          BITOMEGA_LOCK,
          BITOMEGA_DIR_RECURSE,
      },
      {
          "NOISE_TO_VOID",
          BITOMEGA_NOISE,
          BITOMEGA_DIR_NONE,
          0.20f,
          0.95f,
          {0.15f, 0.98f, 0.95f, 0.40f, 0xB002u},
          BITOMEGA_VOID,
          BITOMEGA_DIR_NULL,
      },
      {
          "VOID_DETERMINISTIC_RECOVERY",
          BITOMEGA_VOID,
          BITOMEGA_DIR_NULL,
          0.95f,
          0.08f,
          {0.95f, 0.08f, 0.05f, 0.20f, 0xB003u},
          BITOMEGA_ZERO,
          BITOMEGA_DIR_NONE,
      },
  };

  FILE *csv = fopen("bench/results/bitomega_transitions.csv", "w");
  if (!csv) {
    fprintf(stderr, "bitomega_smoketest: failed to create CSV output\n");
    return 2;
  }

  if (fprintf(csv, "scenario,state_prev,context,state_new,direction\n") < 0) {
    fclose(csv);
    return 3;
  }

  for (size_t i = 0; i < (sizeof(cases) / sizeof(cases[0])); ++i) {
    bitomega_node_t node;
    bitomega_status_t status;

    node.state = cases[i].start_state;
    node.dir = cases[i].start_dir;
    node.coherence = cases[i].start_coh;
    node.entropy = cases[i].start_ent;

    status = bitomega_transition(&node, &cases[i].ctx);
    if (status != BITOMEGA_OK) {
      fprintf(stderr, "bitomega_smoketest: transition failed for %s (%d)\n", cases[i].name, (int)status);
      fclose(csv);
      return 4;
    }
    if (!bitomega_invariant_ok(&node)) {
      fprintf(stderr, "bitomega_smoketest: invariant check failed for %s\n", cases[i].name);
      fclose(csv);
      return 5;
    }
    if (node.state != cases[i].expected_state || node.dir != cases[i].expected_dir) {
      fprintf(stderr,
              "bitomega_smoketest: scenario %s mismatch expected=(%s,%s) got=(%s,%s)\n",
              cases[i].name,
              bitomega_state_name(cases[i].expected_state),
              bitomega_dir_name(cases[i].expected_dir),
              bitomega_state_name(node.state),
              bitomega_dir_name(node.dir));
      fclose(csv);
      return 6;
    }

    if (!append_transition_csv(csv, cases[i].name, cases[i].start_state, &cases[i].ctx, node.state, node.dir)) {
      fclose(csv);
      return 7;
    }
  }

  fclose(csv);
  printf("bitomega_smoketest: %zu scenarios OK\n", sizeof(cases) / sizeof(cases[0]));
  return 0;
}
