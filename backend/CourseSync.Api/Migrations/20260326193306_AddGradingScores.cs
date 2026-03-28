using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CourseSync.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddGradingScores : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "count",
                table: "course_grading_elements",
                type: "integer",
                nullable: false,
                defaultValue: 1);

            migrationBuilder.CreateTable(
                name: "course_grading_scores",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    course_grading_element_id = table.Column<Guid>(type: "uuid", nullable: false),
                    number = table.Column<int>(type: "integer", nullable: false),
                    score = table.Column<decimal>(type: "numeric(6,2)", nullable: false, defaultValue: 0m)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_course_grading_scores", x => x.id);
                    table.ForeignKey(
                        name: "FK_course_grading_scores_course_grading_elements_course_gradin~",
                        column: x => x.course_grading_element_id,
                        principalTable: "course_grading_elements",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_course_grading_scores_course_grading_element_id",
                table: "course_grading_scores",
                column: "course_grading_element_id");

            migrationBuilder.CreateIndex(
                name: "IX_course_grading_scores_course_grading_element_id_number",
                table: "course_grading_scores",
                columns: new[] { "course_grading_element_id", "number" },
                unique: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "course_grading_scores");

            migrationBuilder.DropColumn(
                name: "count",
                table: "course_grading_elements");
        }
    }
}
