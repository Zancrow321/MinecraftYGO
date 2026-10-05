// Prints the native layout of the public ocgcore structs as JSON (one object per struct:
// "size", "align" and per-field offsets). The engine's FFM layouts are verified against this.
#include <cstddef>
#include <cstdio>
#include "ocgapi_types.h"

#define FIELD(type, field) std::printf(",\"%s\":%zu", #field, offsetof(type, field))
#define BEGIN(type) std::printf("%s\"%s\":{\"size\":%zu,\"align\":%zu", first ? "" : ",", #type, sizeof(type), alignof(type)); first = false
#define END() std::printf("}")

int main() {
	bool first = true;
	std::printf("{");
	BEGIN(OCG_CardData);
	FIELD(OCG_CardData, code); FIELD(OCG_CardData, alias); FIELD(OCG_CardData, setcodes);
	FIELD(OCG_CardData, type); FIELD(OCG_CardData, level); FIELD(OCG_CardData, attribute);
	FIELD(OCG_CardData, race); FIELD(OCG_CardData, attack); FIELD(OCG_CardData, defense);
	FIELD(OCG_CardData, lscale); FIELD(OCG_CardData, rscale); FIELD(OCG_CardData, link_marker);
	END();
	BEGIN(OCG_Player);
	FIELD(OCG_Player, startingLP); FIELD(OCG_Player, startingDrawCount); FIELD(OCG_Player, drawCountPerTurn);
	END();
	BEGIN(OCG_DuelOptions);
	FIELD(OCG_DuelOptions, seed); FIELD(OCG_DuelOptions, flags); FIELD(OCG_DuelOptions, team1);
	FIELD(OCG_DuelOptions, team2); FIELD(OCG_DuelOptions, cardReader); FIELD(OCG_DuelOptions, payload1);
	FIELD(OCG_DuelOptions, scriptReader); FIELD(OCG_DuelOptions, payload2); FIELD(OCG_DuelOptions, logHandler);
	FIELD(OCG_DuelOptions, payload3); FIELD(OCG_DuelOptions, cardReaderDone); FIELD(OCG_DuelOptions, payload4);
	FIELD(OCG_DuelOptions, enableUnsafeLibraries);
	END();
	BEGIN(OCG_NewCardInfo);
	FIELD(OCG_NewCardInfo, team); FIELD(OCG_NewCardInfo, duelist); FIELD(OCG_NewCardInfo, code);
	FIELD(OCG_NewCardInfo, con); FIELD(OCG_NewCardInfo, loc); FIELD(OCG_NewCardInfo, seq); FIELD(OCG_NewCardInfo, pos);
	END();
	BEGIN(OCG_QueryInfo);
	FIELD(OCG_QueryInfo, flags); FIELD(OCG_QueryInfo, con); FIELD(OCG_QueryInfo, loc);
	FIELD(OCG_QueryInfo, seq); FIELD(OCG_QueryInfo, overlay_seq);
	END();
	std::printf("}\n");
	return 0;
}
